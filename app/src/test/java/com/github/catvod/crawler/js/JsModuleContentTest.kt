package com.github.catvod.crawler.js

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class JsModuleContentTest {

    @Test
    fun rejectsMissingAndWhitespaceOnlyContent() {
        assertTrue(JsModuleContent.isInvalid(null))
        assertTrue(JsModuleContent.isInvalid(""))
        assertTrue(JsModuleContent.isInvalid("  \n\t "))
    }

    @Test
    fun rejectsHtmlAndHttpNotFoundBodiesRegardlessOfCase() {
        assertTrue(JsModuleContent.isInvalid("<!DOCTYPE html><title>404</title>"))
        assertTrue(JsModuleContent.isInvalid("<HTML><body>Gateway Error</body></HTML>"))
        assertTrue(JsModuleContent.isInvalid("NOT FOUND"))
        assertTrue(JsModuleContent.isInvalid("404 Not Found"))
    }

    @Test
    fun rejectsJsonNotFoundPayloads() {
        assertTrue(JsModuleContent.isInvalid("""{"code":404,"message":"not found"}"""))
        assertTrue(JsModuleContent.isInvalid("""{ "code" : 404, "message" : "not found" }"""))
    }

    @Test
    fun stripsBomBeforeCheckingErrorBody() {
        assertTrue(JsModuleContent.isInvalid("\uFEFF  <html>error</html>"))
    }

    @Test
    @Test
    fun errorDetectionDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(JsModuleContent.isInvalid("NOT FOUND"))
            assertTrue(JsModuleContent.isInvalid("""{ "code" : 404 }"""))
        } finally {
            Locale.setDefault(previous)
        }
    }

    fun acceptsValidJavaScriptAndJsonText() {
        assertFalse(JsModuleContent.isInvalid("export default { home: () => [] }"))
        assertFalse(JsModuleContent.isInvalid("""{"code":200,"data":[]}"""))
        assertFalse(JsModuleContent.isInvalid("const value = 404; export default value;"))
    }
}
