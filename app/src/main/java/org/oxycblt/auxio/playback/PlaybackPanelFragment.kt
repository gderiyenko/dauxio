/*
 * Copyright (c) 2021 Auxio Project
 * PlaybackPanelFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.playback

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentPlaybackPanelBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.queue.QueueViewModel
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.playback.ui.StyledSeekBar
import org.oxycblt.auxio.playback.ui.stepper.Direction
import org.oxycblt.auxio.playback.ui.stepper.StepperOverlay
import org.oxycblt.auxio.playback.ui.swiper.CarouselTransformer
import org.oxycblt.auxio.playback.ui.swiper.CoverPagerAdapter
import org.oxycblt.auxio.playback.ui.swiper.UserAwarePagerCallback
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.dampen
import org.oxycblt.auxio.util.recycler
import org.oxycblt.auxio.util.showToast
import org.oxycblt.auxio.util.smoothScrollByPageTo
import org.oxycblt.auxio.util.systemBarInsetsCompat
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [ViewBindingFragment] more information about the currently playing song, alongside all
 * available controls.
 *
 * TODO: Improve flickering situation on play button
 */
@AndroidEntryPoint
class PlaybackPanelFragment :
    ViewBindingFragment<FragmentPlaybackPanelBinding>(),
    Toolbar.OnMenuItemClickListener,
    StyledSeekBar.Listener,
    StepperOverlay.Listener {
    private val coverPagerAdapter = CoverPagerAdapter(this)
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val listModel: ListViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val queueModel: QueueViewModel by viewModels()
    private var pendingDeleteJob: Job? = null
    private var deleteResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var userAwarePagerCallback: UserAwarePagerCallback? = null

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentPlaybackPanelBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentPlaybackPanelBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        deleteResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result
                ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    musicModel.refresh()
                    context?.showToast(R.string.lng_songs_deleted)
                }
            }

        // --- UI SETUP ---
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.systemBarInsetsCompat
            view.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.playbackToolbar.apply {
            setNavigationOnClickListener { playbackModel.openMain() }
            setOnMenuItemClickListener(this@PlaybackPanelFragment)
        }

        binding.playbackPager.apply {
            // intentional LTR override since thepager is a chronological element
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            adapter = coverPagerAdapter
            userAwarePagerCallback =
                UserAwarePagerCallback(this) {
                        // Posting the queue goto command prevents the seekbar pos from desyncing
                        // from the song's duration, which creates a visual flicker in the seekbar.
                        post { queueModel.goto(it) }
                    }
                    .also { it.attach() }
            setPageTransformer(CarouselTransformer())
            recycler().apply {
                // Make it possible to collapse the bottom sheet from the ViewPager's touch area.
                isNestedScrollingEnabled = false
                // Visual effect consistency
                // TODO: Custom overscroll?
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            // Make it easier to collapse the bottom sheet
            dampen()
            offscreenPageLimit = 1
        }

        // Set up fast seek overlay
        binding.playbackSong.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentSong() }
        }
        binding.playbackArtist.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentArtist() }
        }
        binding.playbackAlbum?.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentAlbum() }
        }

        binding.playbackSeekBar?.listener = this

        // Set up actions
        // TODO: Add better playback button accessibility
        binding.playbackRepeat.setOnClickListener { playbackModel.toggleRepeatMode() }
        binding.playbackSkipPrev.setOnClickListener { playbackModel.prev() }
        binding.playbackPlayPause.apply {
            @SuppressLint("RestrictedApi")
            setCornerSpringForce(
                SpringForce().apply {
                    stiffness = 700f
                    dampingRatio = 0.9f
                }
            )
            setOnClickListener { playbackModel.togglePlaying() }
        }
        binding.playbackSkipNext.setOnClickListener { playbackModel.next() }
        binding.playbackShuffle.setOnClickListener { playbackModel.toggleShuffled() }
        binding.playbackMore?.setOnClickListener {
            playbackModel.song.value?.let {
                listModel.openMenu(R.menu.playback_song, it, PlaySong.ByItself)
            }
        }
        binding.playbackFindLyrics?.setOnClickListener {
            playbackModel.song.value?.let { song ->
                val context = requireContext()
                val artist = song.artists.resolveNames(context)
                val songName = song.name.resolve(context)
                val query = "$artist $songName lyrics".trim()
                val webIntent =
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                    }
                try {
                    startActivity(webIntent)
                } catch (e: ActivityNotFoundException) {
                    val fallbackIntent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
                        )
                    try {
                        startActivity(fallbackIntent)
                    } catch (e2: ActivityNotFoundException) {
                        context.showToast(R.string.err_no_app)
                    }
                }
            }
        }

        // --- VIEWMODEL SETUP --
        collectImmediately(playbackModel.song, ::updateSong)
        collectImmediately(playbackModel.parent, ::updateParent)
        collectImmediately(playbackModel.positionDs, ::updatePosition)
        collectImmediately(playbackModel.repeatMode, ::updateRepeat)
        collectImmediately(playbackModel.isPlaying, ::updatePlaying)
        collectImmediately(playbackModel.isShuffled, ::updateShuffled)
        collectImmediately(playbackModel.pagerQueue, ::updatePager)
    }

    override fun onDestroyBinding(binding: FragmentPlaybackPanelBinding) {
        deleteResultLauncher = null
        binding.playbackRepeat.clearPendingIcon()
        binding.playbackSong.isSelected = false
        binding.playbackArtist.isSelected = false
        binding.playbackAlbum?.isSelected = false
        binding.playbackToolbar.setOnMenuItemClickListener(null)
        userAwarePagerCallback?.release()
        binding.playbackPager.adapter = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete_current_song) {
            val songToDelete = playbackModel.song.value ?: return true
            L.d("Initiating deletion for song: $songToDelete")

            // Skip to next track if playback is ongoing
            playbackModel.next()

            // Cancel any previously scheduled deletion
            pendingDeleteJob?.cancel()

            val binding = binding ?: return true
            val root = binding.root

            var isUndone = false
            var countdown = 3

            val snackbar =
                Snackbar.make(
                        root,
                        getString(R.string.fmt_song_delete_countdown, countdown),
                        Snackbar.LENGTH_INDEFINITE,
                    )
                    .setAction(R.string.lbl_undo) {
                        isUndone = true
                        pendingDeleteJob?.cancel()
                        L.d("Song deletion undone for $songToDelete")
                    }
            snackbar.show()

            pendingDeleteJob =
                viewLifecycleOwner.lifecycleScope.launch {
                    while (countdown > 1) {
                        delay(1000)
                        countdown--
                        if (isUndone) break
                        snackbar.setText(getString(R.string.fmt_song_delete_countdown, countdown))
                    }
                    if (!isUndone) {
                        delay(1000)
                        snackbar.dismiss()
                        executeSongDeletion(songToDelete)
                    }
                }
            return true
        }

        return false
    }

    private fun executeSongDeletion(song: Song) {
        val context = context ?: return
        val resolver = context.contentResolver

        playbackModel.removeSong(song)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                song.uri.authority == MediaStore.AUTHORITY
        ) {
            val mediaStoreUris = listOf(song.uri)
            val pendingIntent = MediaStore.createDeleteRequest(resolver, mediaStoreUris)
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            deleteResultLauncher?.launch(request)
        } else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (DocumentsContract.isDocumentUri(context, song.uri)) {
                        DocumentsContract.deleteDocument(resolver, song.uri)
                    } else {
                        resolver.delete(song.uri, null, null)
                    }
                } catch (e: Exception) {
                    L.e(e, "Failed to delete song file: ${song.uri}")
                }
                withContext(Dispatchers.Main) {
                    musicModel.refresh()
                    context.showToast(R.string.lng_songs_deleted)
                }
            }
        }
    }

    override fun onSeekConfirmed(positionDs: Long) {
        playbackModel.seekTo(positionDs)
    }

    private fun updateSong(song: Song?) {
        if (song == null) {
            // Nothing to do.
            return
        }

        val binding = requireBinding()
        val context = requireContext()
        L.d("Updating song display: $song")
        binding.playbackSong.text = song.name.resolve(context)
        binding.playbackArtist.text = song.artists.resolveNames(context)
        binding.playbackAlbum?.text = song.album.name.resolve(context)
        binding.playbackSeekBar?.durationDs = song.durationMs.msToDs()
    }

    private fun updateParent(parent: MusicParent?) {
        val binding = requireBinding()
        val context = requireContext()
        binding.playbackToolbar.subtitle =
            parent?.run { name.resolve(context) } ?: context.getString(R.string.lbl_all_songs)
    }

    private fun updatePosition(positionDs: Long) {
        requireBinding().playbackSeekBar?.positionDs = positionDs
    }

    private fun updateRepeat(repeatMode: RepeatMode) {
        val repeatButton = requireBinding().playbackRepeat
        repeatButton.isChecked = repeatMode != RepeatMode.NONE
        repeatButton.setIconResource(repeatMode.icon)
    }

    private fun updatePlaying(isPlaying: Boolean) {
        requireBinding().playbackPlayPause.isChecked = isPlaying
        requireBinding().playbackSeekBar?.setWaveEnabled(isPlaying)
    }

    private fun updateShuffled(isShuffled: Boolean) {
        requireBinding().playbackShuffle.isChecked = isShuffled
    }

    private fun updatePager(queue: PagerQueue) {
        val pager = requireBinding().playbackPager
        if (!pager.isAttachedToWindow) {
            pager.post { updatePagerImpl(queue) }
            return
        }

        // Post on animation frame so the layout stabilizes before scrolling/updating pages
        pager.postOnAnimation {
            updatePagerImpl(queue)
        }
    }

    private fun updatePagerImpl(queue: PagerQueue) {
        // Android insanity means this may be executed after view destruction
        // but only on some devices.
        val binding = binding ?: return

        val command = playbackModel.pagerCommand.consume()
        if (command == null) {
            // This probably shouldn't happen in practice, as QueueViewModel directly
            // attaches to PlaybackStateManager and will basically always initialize
            // with a command as a result.
            //
            // If it does happen we should just make sure the UI state is aligned. Don't
            // want broken UI.
            coverPagerAdapter.update(queue.queue, null)
            binding.playbackPager.setCurrentItem(queue.index, false)
            return
        }

        if (command.update != null) {
            // queue needs to be updated.
            coverPagerAdapter.update(queue.queue, command.update)
        }

        if (command.scroll != null) {
            // we need to scroll, however the smooth scroll only really looks best
            // when we are only doing next/prev due to various factors. better to
            // just not animate on outright gotos or queue updates
            val delta = binding.playbackPager.currentItem - command.scroll
            if (delta == 0) {
                // user scroll, carry on
                return
            }
            if (command.update == null && abs(delta) == 1) {
                binding.playbackPager.smoothScrollByPageTo(command.scroll)
            } else {
                binding.playbackPager.setCurrentItem(command.scroll, false)
            }
        }
    }

    private fun navigateToCurrentSong() {
        playbackModel.song.value?.let {
            playbackModel.openMain()
            detailModel.showAlbum(it)
        }
    }

    private fun navigateToCurrentArtist() {
        playbackModel.song.value?.let {
            playbackModel.openMain()
            detailModel.showArtist(it)
        }
    }

    private fun navigateToCurrentAlbum() {
        playbackModel.song.value?.let {
            playbackModel.openMain()
            detailModel.showAlbum(it.album)
        }
    }

    override fun seek(direction: Direction) {
        when (direction) {
            Direction.FORWARDS -> playbackModel.stepForward()
            Direction.BACKWARDS -> playbackModel.stepBackwards()
        }
    }
}
