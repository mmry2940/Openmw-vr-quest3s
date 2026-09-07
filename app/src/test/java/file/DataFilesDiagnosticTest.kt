package file

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DataFilesDiagnosticTest {

    @Test
    fun testUnconfiguredPath() {
        val result = DataFilesDiagnostic.check(
            context = null,
            customPath = ""
        )
        assertEquals(DataFilesDiagnostic.DiagnosticStatus.NOT_CONFIGURED, result.status)
        assertFalse(result.isValid)
        assertTrue(result.summaryTitle.contains("Not Configured"))
    }

    @Test
    fun testNonExistentFolder() {
        val fakePath = "/path/that/does/not/exist/at/all"
        val result = DataFilesDiagnostic.check(null, fakePath)

        assertEquals(DataFilesDiagnostic.DiagnosticStatus.NOT_FOUND, result.status)
        assertFalse(result.isValid)
        assertTrue(result.summaryTitle.contains("Not Found"))
    }

    @Test
    fun testMissingMorrowindEsm() {
        val root = createTempDir(prefix = "mw-test-missing-esm")
        val dataFiles = File(root, "Data Files").apply { mkdirs() }
        File(dataFiles, "Morrowind.bsa").writeBytes(ByteArray(1024 * 100))

        val result = DataFilesDiagnostic.check(null, root.absolutePath)

        assertEquals(DataFilesDiagnostic.DiagnosticStatus.MISSING_MORROWIND_ESM, result.status)
        assertFalse(result.isValid)
        assertFalse(result.morrowindEsmFound)
        assertTrue(result.morrowindBsaFound)
        assertTrue(result.summaryTitle.contains("Missing Morrowind.esm"))
    }

    @Test
    fun testMissingMorrowindBsa() {
        val root = createTempDir(prefix = "mw-test-missing-bsa")
        val dataFiles = File(root, "Data Files").apply { mkdirs() }
        File(dataFiles, "Morrowind.esm").writeBytes(ByteArray(1024 * 100))

        val result = DataFilesDiagnostic.check(null, root.absolutePath)

        assertEquals(DataFilesDiagnostic.DiagnosticStatus.MISSING_CORE_ARCHIVES, result.status)
        assertFalse(result.isValid)
        assertTrue(result.morrowindEsmFound)
        assertFalse(result.morrowindBsaFound)
        assertTrue(result.summaryTitle.contains("Missing Game Archives"))
    }

    @Test
    fun testEmptyMorrowindEsm() {
        val root = createTempDir(prefix = "mw-test-empty-esm")
        val dataFiles = File(root, "Data Files").apply { mkdirs() }
        File(dataFiles, "Morrowind.esm").createNewFile() // 0 bytes
        File(dataFiles, "Morrowind.bsa").writeBytes(ByteArray(1024 * 100))

        val result = DataFilesDiagnostic.check(null, root.absolutePath)

        assertEquals(DataFilesDiagnostic.DiagnosticStatus.EMPTY_MORROWIND_ESM, result.status)
        assertFalse(result.isValid)
        assertTrue(result.summaryTitle.contains("Corrupted"))
    }

    @Test
    fun testValidInstallationWithExpansions() {
        val root = createTempDir(prefix = "mw-test-valid")
        val dataFiles = File(root, "Data Files").apply { mkdirs() }
        File(dataFiles, "Morrowind.esm").writeBytes(ByteArray(1024 * 100))
        File(dataFiles, "Morrowind.bsa").writeBytes(ByteArray(1024 * 500))
        File(dataFiles, "Tribunal.esm").writeBytes(ByteArray(1024 * 50))
        File(dataFiles, "Bloodmoon.esm").writeBytes(ByteArray(1024 * 50))
        File(dataFiles, "Tribunal.bsa").writeBytes(ByteArray(1024 * 200))
        File(dataFiles, "Bloodmoon.bsa").writeBytes(ByteArray(1024 * 200))
        File(dataFiles, "Music").mkdir()
        File(dataFiles, "Sound").mkdir()
        File(root, "Morrowind.ini").writeText("[General]\nTest=1\n")

        val result = DataFilesDiagnostic.check(null, root.absolutePath)

        assertEquals(DataFilesDiagnostic.DiagnosticStatus.OK, result.status)
        assertTrue(result.isValid)
        assertTrue(result.morrowindEsmFound)
        assertTrue(result.morrowindBsaFound)
        assertTrue(result.tribunalFound)
        assertTrue(result.bloodmoonFound)
        assertTrue(result.iniFound)
        assertEquals(3, result.esmFiles.size)
        assertEquals(3, result.bsaFiles.size)
    }

    @Test
    fun testFormatHumanSize() {
        assertEquals("0 B", DataFilesDiagnostic.formatHumanSize(0))
        assertEquals("500 B", DataFilesDiagnostic.formatHumanSize(500))
        assertEquals("10.0 KB", DataFilesDiagnostic.formatHumanSize(10240))
        assertEquals("79.2 MB", DataFilesDiagnostic.formatHumanSize((79.2 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", DataFilesDiagnostic.formatHumanSize((1.25 * 1024 * 1024 * 1024).toLong()))
    }
}
