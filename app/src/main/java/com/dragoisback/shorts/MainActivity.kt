package com.dragoisback.shorts

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: View
    private lateinit var pager: ViewPager2
    private lateinit var adapter: ShortsAdapter
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var counter: TextView
    private lateinit var muteButton: ImageButton
    private lateinit var changeFolderButton: ImageButton

    private var player: ExoPlayer? = null
    private var currentPage = 0
    private var muted = false
    private var userPaused = false
    private var errorStreak = 0

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        persistFolderPermission(uri)
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        loadLibrary(uri)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) errorStreak = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            // Typically an unsupported container/codec (AVI, FLV, exotic streams).
            // Skip past the broken file instead of leaving a frozen black page.
            val total = adapter.itemCount
            if (total == 0) return
            errorStreak++
            if (errorStreak >= minOf(total, MAX_CONSECUTIVE_ERRORS)) {
                errorStreak = 0
                Toast.makeText(this@MainActivity, R.string.unplayable_batch, Toast.LENGTH_LONG)
                    .show()
                return
            }
            Toast.makeText(this@MainActivity, R.string.unplayable_skip, Toast.LENGTH_SHORT).show()
            pager.setCurrentItem((currentPage + 1) % total, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("shorts", MODE_PRIVATE)
        root = findViewById(R.id.root)
        pager = findViewById(R.id.pager)
        emptyState = findViewById(R.id.empty_state)
        emptyText = findViewById(R.id.empty_text)
        progress = findViewById(R.id.progress)
        counter = findViewById(R.id.counter)
        muteButton = findViewById(R.id.mute_button)
        changeFolderButton = findViewById(R.id.change_folder_button)

        // Draw behind the system bars (enforced on Android 15+, so make it uniform);
        // setupInsets() keeps the overlays clear of the bars and the camera cutout.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupInsets()

        // Videos are meant to be watched, not dimmed after 30 s of no touching.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        adapter = ShortsAdapter(mutableListOf()) { togglePlayPause() }
        pager.adapter = adapter
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pager.offscreenPageLimit = 1
        (pager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                // Swiping to a new short always means "play it".
                userPaused = false
                playPage(position)
                updateCounter()
            }
        })

        findViewById<Button>(R.id.choose_folder_button).setOnClickListener { openPicker() }
        changeFolderButton.setOnClickListener { openPicker() }
        muteButton.setOnClickListener { toggleMute() }

        muted = prefs.getBoolean(KEY_MUTED, false)
        updateMuteIcon()

        val saved = prefs.getString(KEY_FOLDER, null)
        if (saved != null) {
            loadLibrary(Uri.parse(saved))
        } else {
            showEmpty(getString(R.string.empty_hint))
        }
    }

    /** Pads the top overlays below the status bar / punch-hole cutout (edge-to-edge). */
    private fun setupInsets() {
        val overlays = listOf(counter, muteButton, changeFolderButton)
        val baseTops = overlays.associateWith {
            (it.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            overlays.forEach { v ->
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = baseTops.getValue(v) + bars.top
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun persistFolderPermission(uri: Uri) {
        val readWrite =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, readWrite)
        } catch (e: SecurityException) {
            try {
                // Some providers only hand out read access.
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e2: SecurityException) {
                // No persistable grant: the folder still works for this session.
            }
        }
    }

    private fun openPicker() {
        try {
            pickFolder.launch(null)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLibrary(treeUri: Uri) {
        progress.visibility = View.VISIBLE
        releasePlayer()
        io.execute {
            val result = try {
                VideoScanner.scan(this, treeUri)
            } catch (e: Exception) {
                ScanResult.Inaccessible
            }
            main.post {
                if (isDestroyed) return@post
                progress.visibility = View.GONE
                when (result) {
                    ScanResult.Inaccessible -> {
                        adapter.replaceAll(emptyList())
                        showEmpty(getString(R.string.folder_inaccessible))
                    }

                    is ScanResult.Success ->
                        if (result.items.isEmpty()) {
                            adapter.replaceAll(emptyList())
                            showEmpty(getString(R.string.empty_folder))
                        } else {
                            showFeed(result.items)
                        }
                }
            }
        }
    }

    private fun showFeed(items: List<VideoItem>) {
        emptyState.visibility = View.GONE
        pager.visibility = View.VISIBLE
        counter.visibility = View.VISIBLE
        muteButton.visibility = View.VISIBLE
        adapter.replaceAll(items)
        errorStreak = 0
        userPaused = false
        currentPage = 0
        if (pager.currentItem != 0) {
            // onPageSelected(0) will kick playback off.
            pager.setCurrentItem(0, false)
        } else {
            pager.post { if (currentPage == 0) playPage(0) }
        }
        updateCounter()
    }

    private fun showEmpty(message: String) {
        emptyText.text = message
        emptyState.visibility = View.VISIBLE
        pager.visibility = View.GONE
        counter.visibility = View.GONE
        muteButton.visibility = View.GONE
        releasePlayer()
    }

    private fun ensurePlayer(): ExoPlayer {
        var p = player
        if (p == null) {
            p = ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = if (muted) 0f else 1f
                    addListener(playerListener)
                }
            player = p
        }
        return p
    }

    /** Moves the single shared player onto the page at [position] and starts it. */
    private fun playPage(position: Int, attempt: Int = 0) {
        val item = adapter.itemAt(position) ?: return
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position) as? ShortsAdapter.PageHolder
        if (holder == null) {
            // View not attached yet – retry on the next frame (bounded, no infinite loop).
            if (attempt < MAX_ATTACH_ATTEMPTS) {
                pager.post { if (currentPage == position) playPage(position, attempt + 1) }
            }
            return
        }

        val p = ensurePlayer()

        // Detach the player from any other page still holding it.
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i)
            val pv = child.findViewById<PlayerView>(R.id.player_view)
            if (pv != null && pv !== holder.playerView) pv.player = null
        }

        holder.playerView.player = p
        holder.playerView.useController = false
        holder.playIcon.visibility = if (userPaused) View.VISIBLE else View.GONE

        p.setMediaItem(MediaItem.fromUri(item.uri))
        p.prepare()
        p.playWhenReady = !userPaused
    }

    private fun togglePlayPause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
        userPaused = !p.playWhenReady
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val holder =
            recycler.findViewHolderForAdapterPosition(currentPage) as? ShortsAdapter.PageHolder
        holder?.playIcon?.visibility = if (p.playWhenReady) View.GONE else View.VISIBLE
    }

    private fun toggleMute() {
        muted = !muted
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        player?.volume = if (muted) 0f else 1f
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        muteButton.setImageResource(
            if (muted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun updateCounter() {
        val total = adapter.itemCount
        counter.text = if (total == 0) "" else getString(
            R.string.counter_format, currentPage + 1, total
        )
    }

    private fun releasePlayer() {
        player?.let { p ->
            p.removeListener(playerListener)
            val recycler = pager.getChildAt(0) as? RecyclerView
            if (recycler != null) {
                for (i in 0 until recycler.childCount) {
                    recycler.getChildAt(i)
                        .findViewById<PlayerView>(R.id.player_view)?.player = null
                }
            }
            p.release()
        }
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        if (adapter.itemCount == 0) return
        if (player == null) {
            if (!userPaused) playPage(currentPage)
        } else {
            player?.playWhenReady = !userPaused
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        releasePlayer()
        io.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_MUTED = "muted"
        private const val MAX_ATTACH_ATTEMPTS = 10
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }
}
