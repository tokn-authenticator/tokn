package me.diamondforge.tokn.data.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackParserTest {

    private val validUuid = "a3bb189e-8bf9-3888-9912-ace4e6543002"

    @Test
    fun `parses happy-path pack with multiple icons`() {
        val json = """
            {
              "uuid": "$validUuid",
              "name": "Sample Pack",
              "version": 4,
              "icons": [
                {
                  "filename": "google.svg",
                  "name": "Google",
                  "category": "auth",
                  "issuer": ["Google", "Google Cloud"]
                },
                {
                  "filename": "github.png",
                  "name": "GitHub",
                  "issuer": ["GitHub"]
                }
              ]
            }
        """.trimIndent()

        val pack = IconPackParser.parse(json)
        assertEquals(validUuid, pack.uuid)
        assertEquals("Sample Pack", pack.name)
        assertEquals(4, pack.version)
        assertEquals(2, pack.icons.size)
        assertEquals("google.svg", pack.icons[0].filename)
        assertEquals("auth", pack.icons[0].category)
        assertEquals(listOf("Google", "Google Cloud"), pack.icons[0].issuerMatches)
        assertNull(pack.icons[1].category)
    }

    @Test
    fun `missing version defaults to 1`() {
        val json = """{"uuid":"$validUuid","name":"x","icons":[]}"""
        assertEquals(1, IconPackParser.parse(json).version)
    }

    @Test
    fun `icon without name falls back to filename basename`() {
        val json = """
            {"uuid":"$validUuid","name":"x","icons":[
              {"filename":"icons/cloud-providers/aws.png"}
            ]}
        """.trimIndent()
        val pack = IconPackParser.parse(json)
        assertEquals("aws", pack.icons.single().displayName)
    }

    @Test
    fun `icon with blank category resolves to null`() {
        val json = """
            {"uuid":"$validUuid","name":"x","icons":[
              {"filename":"a.svg","name":"A","category":""}
            ]}
        """.trimIndent()
        assertNull(IconPackParser.parse(json).icons.single().category)
    }

    @Test
    fun `icon without issuer array yields empty issuerMatches`() {
        val json = """
            {"uuid":"$validUuid","name":"x","icons":[
              {"filename":"a.svg"}
            ]}
        """.trimIndent()
        assertTrue(IconPackParser.parse(json).icons.single().issuerMatches.isEmpty())
    }

    @Test
    fun `invalid json throws IconPackParseException`() {
        val exception = assertThrows(IconPackParseException::class.java) {
            IconPackParser.parse("not json at all")
        }
        assertTrue(exception.message!!.contains("valid JSON"))
    }

    @Test
    fun `non-uuid uuid value is rejected`() {
        val json = """{"uuid":"not-a-uuid","name":"x","icons":[]}"""
        val exception = assertThrows(IconPackParseException::class.java) {
            IconPackParser.parse(json)
        }
        assertTrue(exception.message!!.contains("Bad UUID"))
    }

    @Test
    fun `missing name is rejected`() {
        val json = """{"uuid":"$validUuid","name":"","icons":[]}"""
        val exception = assertThrows(IconPackParseException::class.java) {
            IconPackParser.parse(json)
        }
        assertTrue(exception.message!!.contains("name missing"))
    }

    @Test
    fun `missing icons array is rejected`() {
        val json = """{"uuid":"$validUuid","name":"x"}"""
        val exception = assertThrows(IconPackParseException::class.java) {
            IconPackParser.parse(json)
        }
        assertTrue(exception.message!!.contains("icons[]"))
    }

    @Test
    fun `icon missing filename is rejected with index in message`() {
        val json = """
            {"uuid":"$validUuid","name":"x","icons":[
              {"filename":"ok.svg"},
              {"name":"oops"}
            ]}
        """.trimIndent()
        val exception = assertThrows(IconPackParseException::class.java) {
            IconPackParser.parse(json)
        }
        assertTrue(exception.message!!.contains("icons[1]"))
    }

    @Test
    fun `explicit null category resolves to null`() {
        val json = """
            {"uuid":"$validUuid","name":"x","icons":[
              {"filename":"a.svg","category":null}
            ]}
        """.trimIndent()
        assertNull(IconPackParser.parse(json).icons.single().category)
    }
}
