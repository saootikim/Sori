package com.metrolist.music.sori

import com.metrolist.innertube.YouTubeConstants
import com.metrolist.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSectionTitleTest {
    @Test
    fun mapsInnertubeGroupNamesToStrings() {
        assertEquals(R.string.sori_top_result, searchSectionTitleRes(YouTubeConstants.DEFAULT_TOP_RESULT))
        assertEquals(R.string.filter_songs, searchSectionTitleRes("Songs"))
        assertEquals(R.string.filter_videos, searchSectionTitleRes("Videos"))
        assertEquals(R.string.filter_albums, searchSectionTitleRes("Albums"))
        assertEquals(R.string.filter_artists, searchSectionTitleRes("Artists"))
        assertEquals(R.string.playlists, searchSectionTitleRes("Playlists"))
        assertEquals(R.string.sori_other_results, searchSectionTitleRes(YouTubeConstants.DEFAULT_OTHER_RESULTS))
    }

    @Test
    fun keepsTitlesYouTubeAlreadyLocalized() {
        assertNull(searchSectionTitleRes("노래"))
        assertNull(searchSectionTitleRes("커뮤니티 재생목록"))
    }
}
