/*
 * Copyright (c) 2022 Auxio Project
 * SelectionFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.AuxioToolbar
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Song

/** A subset of ListFragment that implements aspects of the selection UI. */
abstract class SelectionFragment<VB : ViewBinding> :
    ViewBindingFragment<VB>(), Toolbar.OnMenuItemClickListener {
    protected abstract val listModel: ListViewModel
    protected abstract val musicModel: MusicViewModel
    protected abstract val playbackModel: PlaybackViewModel

    private var deleteResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    open fun getSelectionToolbar(binding: VB): AuxioToolbar? = null

    override fun onBindingCreated(binding: VB, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        deleteResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result
                ->
                if (result.resultCode == Activity.RESULT_OK) {
                    musicModel.refresh()
                    requireContext().showToast(R.string.lng_songs_deleted)
                }
            }

        collect(musicModel.songDeleteDecision.flow) { songs ->
            if (songs != null) {
                promptDeleteSongs(songs)
                musicModel.songDeleteDecision.consume()
            }
        }

        getSelectionToolbar(binding)?.apply {
            // Add cancel and menu item listeners to manage what occurs with the selection.
            setNavigationOnClickListener { listModel.dropSelection() }
            setOnMenuItemClickListener(this@SelectionFragment)
            setOnOverflowMenuClick {
                listModel.openMenu(R.menu.selection, listModel.peekSelection())
            }
        }
    }

    override fun onDestroyBinding(binding: VB) {
        super.onDestroyBinding(binding)
        getSelectionToolbar(binding)?.setOnMenuItemClickListener(null)
    }

    override fun onMenuItemClick(item: MenuItem) =
        when (item.itemId) {
            R.id.action_selection_play_next -> {
                playbackModel.playNext(listModel.takeSelection())
                requireContext().showToast(R.string.lng_play_next)
                true
            }
            R.id.action_selection_playlist_add -> {
                musicModel.addToPlaylist(listModel.takeSelection())
                true
            }
            R.id.action_selection_delete -> {
                promptDeleteSongs(listModel.takeSelection())
                true
            }
            else -> false
        }

    fun promptDeleteSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.lbl_confirm_delete_songs)
            .setMessage(R.string.lbl_delete_songs_info)
            .setPositiveButton(R.string.lbl_delete) { _, _ ->
                performDeleteSongs(songs)
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }

    private fun performDeleteSongs(songs: List<Song>) {
        val resolver = requireContext().contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = mutableListOf<android.net.Uri>()
            val nonMediaStoreSongs = mutableListOf<Song>()
            for (song in songs) {
                if (song.uri.authority == MediaStore.AUTHORITY) {
                    mediaStoreUris.add(song.uri)
                } else {
                    nonMediaStoreSongs.add(song)
                }
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                for (song in nonMediaStoreSongs) {
                    try {
                        if (DocumentsContract.isDocumentUri(requireContext(), song.uri)) {
                            DocumentsContract.deleteDocument(resolver, song.uri)
                        } else {
                            resolver.delete(song.uri, null, null)
                        }
                    } catch (e: Exception) {
                        // Ignore individual deletion errors
                    }
                }

                if (mediaStoreUris.isNotEmpty()) {
                    val pendingIntent = MediaStore.createDeleteRequest(resolver, mediaStoreUris)
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    withContext(Dispatchers.Main) {
                        deleteResultLauncher?.launch(request)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        musicModel.refresh()
                        requireContext().showToast(R.string.lng_songs_deleted)
                    }
                }
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                for (song in songs) {
                    try {
                        if (DocumentsContract.isDocumentUri(requireContext(), song.uri)) {
                            DocumentsContract.deleteDocument(resolver, song.uri)
                        } else {
                            resolver.delete(song.uri, null, null)
                        }
                    } catch (e: Exception) {
                        // Ignore individual deletion errors
                    }
                }
                withContext(Dispatchers.Main) {
                    musicModel.refresh()
                    requireContext().showToast(R.string.lng_songs_deleted)
                }
            }
        }
    }

    // TODO: Re-add the automatic selection handling
}
