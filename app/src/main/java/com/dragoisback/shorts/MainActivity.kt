package com.dragoisback.shorts

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var pager: ViewPager2
    private lateinit var adapter: ShortsAdapter
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var counter: TextView
    private lateinit var muteButton: ImageButton

    private var player: ExoPlayer? = null
    private var currentPage = 0
    private var muted = false

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Some providers do not offer persistable grants; playback still works this session.
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        loadLibrary(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("shorts", MODE_PRIVATE)
        pager = findViewById(R.id.pager)
        emptyState = findViewById(R.id.empty_state)
        emptyText = findViewById(R.id.empty_text)
        progress = findViewById(R.id.progress)
        counter = findViewById(R.id.counter)
        muteButton = findViewById(R.id.mute_button)

        adapter = ShortsAdapter(mutableListOf()) { togglePlayPause() }
        pager.adapter = adapter
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pager.offscreenPageLimit = 1
        (pager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                playPage(position)
                updateCounter()
            }
        })

        findViewById<Button>(R.id.choose_folder_button).setOnClickListener { openPicker() }
        findViewById<ImageButton>(R.id.change_folder_button).setOnClickListener { openPicker() }
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

    private fun openPicker() {
        try {
            pickFolder.launch(null)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLibrary(treeUri: Uri) {
        progress.visibility = View.VISIBLE
        io.execute {
            val items = VideoScanner.scan(this, treeUri)
            main.post {
                progress.visibility = View.GONE
                if (items.isEmpty()) {
                    adapter.replaceAll(emptyList())
                    showEmpty(getString(R.string.empty_folder))
                    releasePlayer()
                } else {
                    emptyState.visibility = View.GONE
                    pager.visibility = View.VISIBLE
                    counter.visibility = View.VISIBLE
                    muteButton.visibility = View.VISIBLE
                    adapter.replaceAll(items)
                    currentPage = 0
                    pager.setCurrentItem(0, false)
                    pager.post { playPage(0) }
                    updateCounter()
                }
            }
        }
    }

    private fun showEmpty(message: String) {
        emptyText.text = message
        emptyState.visibility = View.VISIBLE
        pager.visibility = View.GONE
        counter.visibility = View.GONE
        muteButton.visibility = View.GONE
    }

    private fun ensurePlayer(): ExoPlayer {
        var p = player
        if (p == null) {
            p = ExoPlayer.Builder(this).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
            }
            player = p
        }
        return p
    }

    /** Moves the single shared player onto the page at [position] and starts it. */
    private fun playPage(position: Int) {
        val item = adapter.itemAt(position) ?: return
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position) as? ShortsAdapter.PageHolder
        if (holder == null) {
            // View not attached yet – retry on the next frame.
            pager.post { if (currentPage == position) playPage(position) }
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
        holder.playIcon.visibility = View.GONE

        p.setMediaItem(MediaItem.fromUri(item.uri))
        p.prepare()
        p.playWhenReady = true
    }

    private fun togglePlayPause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
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
        player?.release()
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        if (adapter.itemCount > 0) {
            if (player == null) playPage(currentPage) else player?.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        io.shutdownNow()
    }

    companion object {
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_MUTED = "muted"
    }
}
