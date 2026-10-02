package com.codex.quota

import com.codex.quota.auth.DeviceCodeManager
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthRegressionTest {
    @Test fun exchangeRejected_doesNotTreatCodeAsTokenOrExposeBody() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(400).message("bad").body("secret-error-body".toResponseBody()).build()
        }.build()
        val result = DeviceCodeManager.exchangeAuthorizationCode("one-time-code", "verifier", client)
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("secret-error-body"))
    }

    @Test fun deviceCodeRequest_suppliesPublicClientId() = runTest {
        var payload = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer); payload = buffer.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("ok")
                .body("""{"device_auth_id":"test","user_code":"TEST-ONLY","interval":"5"}""".toResponseBody()).build()
        }.build()
        val result = DeviceCodeManager.requestDeviceCode(client)
        assertTrue(result.isSuccess)
        assertTrue(payload.contains(DeviceCodeManager.OFFICIAL_CLIENT_ID))
    }
}
