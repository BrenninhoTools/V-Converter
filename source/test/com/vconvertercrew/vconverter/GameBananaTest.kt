package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.online.GameBanana
import com.vconvertercrew.vconverter.online.GameBananaException
import com.vconvertercrew.vconverter.online.GameBananaFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBananaTest {
    private val page = """{"_aMetadata":{"_nRecordCount":164534,"_bIsComplete":false,"_nPerpage":15},"_aRecords":[
        {"_idRow":517012,"_sModelName":"Mod","_sName":"Time Bar [V-Slice Mod]","_sProfileUrl":"https://gamebanana.com/mods/517012","_bHasFiles":true,
         "_aPreviewMedia":{"_aImages":[{"_sType":"screenshot","_sBaseUrl":"https://images.gamebanana.com/img/ss/mods","_sFile":"6658d8da19726.jpg","_sFile220":"220-90_6658d8da19726.jpg"}]},
         "_aSubmitter":{"_idRow":1,"_sName":"someone"},"_aRootCategory":{"_sName":"Mod Folders"},"_nLikeCount":132,"_nViewCount":117354},
        {"_idRow":5,"_sModelName":"Wip","_sName":"Not a mod"},
        {"_idRow":516489,"_sModelName":"Mod","_sName":"Combos Reimplemented","_nLikeCount":108}
    ]}"""

    private val profile = """{"_idRow":516489,"_sModelName":"Mod","_sName":"Combos Reimplemented [V-Slice Mod]","_aGame":{"_idRow":8694,"_sName":"Friday Night Funkin'"},
        "_aSuperCategory":{"_sName":"Base Game Mod Folders"},"_aCategory":{"_sName":"Other/Misc (Base)"},"_nDownloadCount":226747,"_nLikeCount":108,
        "_aFiles":[
          {"_idRow":1599940,"_sFile":"combos_reimplemented_143.zip","_nFilesize":1070508,"_sDescription":"Haptics Fix","_tsDateAdded":1768083392,"_nDownloadCount":64439,"_sAnalysisResult":"ok","_sAvResult":"clean","_sDownloadUrl":"https://gamebanana.com/dl/1599940","_sMd5Checksum":"5780d83a"},
          {"_idRow":1897608,"_sFile":"combos_reimplemented_144.zip","_nFilesize":1070218,"_tsDateAdded":1795212480,"_sAnalysisResult":"ok","_sAvResult":"clean","_sDownloadUrl":"https://gamebanana.com/dl/1897608"},
          {"_idRow":1900000,"_sFile":"readme.txt","_nFilesize":10,"_tsDateAdded":1795212999,"_sAnalysisResult":"ok"},
          {"_idRow":1900001,"_sFile":"suspicious.zip","_nFilesize":10,"_tsDateAdded":1795213999,"_sAnalysisResult":"suspicious","_sAvResult":"infected"}
        ]}"""

    @Test
    fun parsesFeedPages() {
        val parsed = GameBanana.parsePage(page)
        assertEquals(2, parsed.mods.size)
        assertEquals(164534, parsed.total)
        assertFalse(parsed.complete)
        val first = parsed.mods[0]
        assertEquals(517012, first.id)
        assertEquals("someone", first.submitter)
        assertEquals("Mod Folders", first.category)
        assertEquals("https://images.gamebanana.com/img/ss/mods/220-90_6658d8da19726.jpg", first.thumbnail)
        assertEquals("https://gamebanana.com/mods/516489", parsed.mods[1].profileUrl)
        assertNull(parsed.mods[1].thumbnail)
    }

    @Test
    fun parsesDetailsAndPicksTheNewestCleanArchive() {
        val details = GameBanana.parseDetails(profile)
        assertEquals(4, details.files.size)
        assertEquals(226747, details.downloads)
        assertEquals("V-Slice", details.engine)
        val best = GameBanana.bestFile(details.files)!!
        assertEquals(1897608, best.id)
        assertTrue(best.isArchive)
        assertFalse(details.files.first { it.id == 1900001 }.clean)
        assertFalse(details.files.first { it.id == 1900000 }.isArchive)
        assertEquals("combos_reimplemented_144.zip", GameBanana.safeFileName(best))
    }

    @Test
    fun rejectsOtherGames() {
        val other = profile.replace("\"_idRow\":8694", "\"_idRow\":6878")
        var failed = false
        try {
            GameBanana.parseDetails(other)
        } catch (e: GameBananaException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun readsLinksAndIds() {
        assertEquals(516489, GameBanana.parseModId("https://gamebanana.com/mods/516489"))
        assertEquals(516489, GameBanana.parseModId("gamebanana.com/mods/download/516489#FileInfo_1"))
        assertEquals(42, GameBanana.parseModId(" 42 "))
        assertNull(GameBanana.parseModId("psych engine"))
        assertEquals(1599940, GameBanana.parseFileId("https://gamebanana.com/dl/1599940"))
        assertEquals("Psych Engine", GameBanana.engineOf("My Psych Engine 1.0 port"))
        assertNull(GameBanana.engineOf("cool songs"))
        assertEquals("a%20b%26c%C3%A9", GameBanana.encode("a b&cé"))
        assertTrue(GameBanana.feedUrl(GameBananaFeed.NEW, 2).contains("/Game/8694/Subfeed?_nPage=2&_sSort=new"))
        assertTrue(GameBanana.searchUrl("week 7", 1).contains("_sSearchString=week%207"))
    }
}
