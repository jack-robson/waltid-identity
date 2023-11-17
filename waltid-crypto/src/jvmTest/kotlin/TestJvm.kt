import id.walt.crypto.keys.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class TestJvm {

    @Test
    fun apiTestAll() = runTest {
        KeyType.entries.forEach { runKeyCompleteFlow(it) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun signatureSpeedTestAll() = runTest(timeout = 5.minutes) {
        KeyType.entries.forEach { keyType ->
            val key = LocalKey.generate(keyType)
            key.signJws("abc".encodeToByteArray())

            val jobs = ArrayList<Job>()

            val n = 10_000
            val dispatchMs = measureTimeMillis {
                repeat(n) {
                    jobs.add(GlobalScope.launch {
                        key.signJws(byteArrayOf(it.toByte()))
                    })
                }
            }

            val signMs = measureTimeMillis {
                jobs.forEach { it.join() }
            }

            println("$keyType: Dispatch $dispatchMs ms, Signing: $signMs ms (for $n signatures)")
        }
    }

    @Test
    fun exampleSignJws() = runTest {
        val localKey by lazy { runBlocking { LocalKey.generate(KeyType.Ed25519) } }

        val payload = JsonObject(
            mapOf(
                "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
                "iss" to JsonPrimitive("http://localhost:3000"),
                "aud" to JsonPrimitive("TOKEN"),
            )
        )

        println("Signing JWS: $payload")
        val signed = localKey.signJws(payload.toString().toByteArray())
        println("Signed: $signed")

        println("Verifying signed: $signed")
        localKey.verifyJws(signed).also { println("Verified: $it") }
    }

    @Test
    @EnabledIf("hostCondition")
    fun testKeySerialization() = runTest {
        val localKey = LocalKey.generate(KeyType.Ed25519)
        val localKeySerialized = KeySerialization.serializeKey(localKey)

        val jsons = listOf(
            localKeySerialized to LocalKey::class,
            """{"type":"tse","server":"http://127.0.0.1:8200/v1/transit","accessKey":"dev-only-token","id":"k-307668075","_publicKey":[-41,-105,-126,77,-74,88,-28,123,93,-81,105,-13,-93,-111,27,81,-90,-1,86,59,68,105,-108,118,-68,121,18,-114,71,-69,-106,-109],"_keyType":"Ed25519"}""" to TSEKey::class,
            """{"type":"tse","server":"http://127.0.0.1:8200/v1/transit","accessKey":"dev-only-token","id":"k-307668075"}""" to TSEKey::class
        )

        jsons.forEach {
            check(it)
        }
    }

    private val testObj = JsonObject(mapOf("value1" to JsonPrimitive("123456789")))

    @Test
    fun testDeserializedVerify() = runTest {
        val testObjJson = Json.encodeToString(testObj)

        val key = LocalKey.generate(KeyType.Ed25519)
        val key2 = KeySerialization.deserializeKey(KeySerialization.serializeKey(key)).getOrThrow()

        val jws = key.signJws(testObjJson.toByteArray())

        val res = key2.verifyJws(jws)
        println(res)
        assertEquals(testObj, res.getOrThrow())
    }


    @Test
    fun testDeserializedSign() = runTest {
        val testObjJson = Json.encodeToString(testObj)

        val keyToUseForVerifying = LocalKey.generate(KeyType.Ed25519)
        val keyToUseForSigning = KeySerialization.deserializeKey(KeySerialization.serializeKey(keyToUseForVerifying)).getOrThrow()

        val jws = keyToUseForSigning.signJws(testObjJson.toByteArray())

        val res = keyToUseForVerifying.verifyJws(jws)
        println(res)
        assertEquals(testObj, res.getOrThrow())
    }

    private suspend fun runKeyCompleteFlow(keyType: KeyType) {
        val plaintext = JsonObject(
            mapOf("id" to JsonPrimitive("abc123-${keyType.name}-JVM"))
        )
        println("Plaintext: $plaintext")

        println("Generating key: $keyType...")
        val key = LocalKey.generate(keyType)

        println("  Checking for private key...")
        assertTrue { key.hasPrivateKey }

        println("  Checking for private key on a public key...")
        assertFalse { key.getPublicKey().hasPrivateKey }

        println("  Key id: " + key.getKeyId())

        println("  Exporting JWK...")
        val exportedJwk = key.exportJWK()
        println("  JWK: $exportedJwk")
        assertTrue { exportedJwk.startsWith("{") }

        println("  Signing...")
        val signed = key.signJws(Json.encodeToString(plaintext).encodeToByteArray())
        println("  Signed: $signed")

        println("  Verifying...")
        val check1 = key.verifyJws(signed)
        assertTrue(check1.isSuccess)
        assertEquals(plaintext, check1.getOrThrow())

        assertEquals(plaintext, check1.getOrThrow())
        println("  Private key: ${check1.getOrNull()}")

        val check2 = key.getPublicKey().verifyJws(signed)
        assertEquals(plaintext, check2.getOrThrow())
        println("  Public key: ${check2.getOrNull()}")
    }

    private suspend fun check(value: Pair<String, KClass<out Key>>) {
        println("Parsing: ${value.first}")
        val key = KeySerialization.deserializeKey(value.first).getOrThrow()

        println("Got key: $key")
        println("Of type: " + key::class.simpleName)

        println("Key ID: ${key.getKeyId()}")
        println("Key type: ${key.keyType}")
        println("Public key: ${key.getPublicKey().exportJWK()}")

        assertEquals(value.second.simpleName, key::class.simpleName)
        assertNotEquals("Key", key::class.simpleName)

        println()
    }

    private fun hostCondition() = runCatching {
        runBlocking { HttpClient().get("http://127.0.0.1:8200") }.status == HttpStatusCode.OK
    }.fold(onSuccess = { true }, onFailure = { false })

}
