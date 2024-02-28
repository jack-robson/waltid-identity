import Credential.Companion.testCredential
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.account.AuthenticationResult
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.LoginRequestJson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class WalletApiTeste2eBase {
//  private val didMethodsToTest = listOf("key", "jwk", "web", "cheqd") //22/02/24 cheqd resolver broken awaiting fix
    
    private val defaultTestUser = User("tester", "user@email.com", "password", "email")
    
    private val didMethodsToTest = listOf("key", "jwk", "web")
    
    private val alphabet = ('a'..'z')
    protected lateinit var token: String
    protected lateinit var walletId: UUID
    protected lateinit var firstDid: String
    
    private fun randomString(length: Int) = (1..length).map { alphabet.random() }.toTypedArray().contentToString()
    
    protected val email = randomString(8) + "@example.org"
    protected val password = randomString(16)
    
    abstract var walletClient: HttpClient
    
    //    abstract var issuerClient: HttpClient
    abstract var walletUrl: String
    abstract var issuerUrl: String

//    abstract fun ApplicationTestBuilder.newClient(token: String? = null)
    
    protected suspend fun testCreateUser(user: User) {
        println("\nUse Case -> Register User $user\n")
        val endpoint = "$walletUrl/wallet-api/auth/create"
        println("POST ($endpoint)\n")
        
        walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "name" to "tester",
                    "email" to user.email,
                    "password" to user.password,
                    "type" to "email"
                ),
            )
        }.let { response ->
            assertEquals(HttpStatusCode.Created, response.status)
        }
    }
    
    protected suspend fun requestCredential(issuanceUri: String, did:String) {
        println("\nUse Case -> Use Offer Request")
        val result = walletClient.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            parameter("did", did)
            
            contentType(ContentType.Text.Plain)
            setBody(issuanceUri)
        }
        println("Claim result: $result")
        assertEquals(HttpStatusCode.OK, result.status)
    }
    
    protected suspend fun issueJwtCredential(): String = run {
        // Issuer
        println("Calling issuer...")
        val issuanceUri = walletClient.post("$walletUrl/openid4vc/jwt/issue") {
            //language=JSON
            setBody(
                testCredential
            )
        }.bodyAsText()

//
        println("Issuance (Offer) URI: $issuanceUri\n")
        return issuanceUri
    }
    
    private suspend fun testExampleKey() = run {
        println("\nUse Case -> Create Example Key")
        val endpoint = "$walletUrl/example-key"
        println("GET ($endpoint)")
        walletClient.get(endpoint) {
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    protected suspend fun login(user: User = defaultTestUser) = run {
        println("Running login...")
        walletClient.post("/wallet-api/auth/login") {
            setBody(
                LoginRequestJson.encodeToString(
                    EmailAccountRequest(
                        email = user.email, password = user.password
                    ) as AccountRequest
                )
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    protected suspend fun getTokenFor(user: User = defaultTestUser) = run {
        println("\nUse Case -> Login with user $user")
        val endpoint = "$walletUrl/wallet-api/auth/login"
        println("POST ($endpoint)")
        token = walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "name" to user.name,
                    "email" to user.email,
                    "password" to user.password,
                    "type" to user.accountType
                )
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonObject>()["token"]?.jsonPrimitive?.content ?: error("No token responded")
        }
        println("Login Successful.")
        println("> Response JSON body token: $token")
    }
    
    protected suspend fun getWallets() {
        println("\nUse Case -> List Wallets for Account\n")
        val endpoint = "$walletUrl/wallet-api/wallet/accounts/wallets"
        println("GET($endpoint)")
        
        val walletListing = walletClient.get("/wallet-api/wallet/accounts/wallets")
            .body<AccountWalletListing>()
        println("Wallet listing: $walletListing\n")
        
        val availableWallets = walletListing.wallets
        assertTrue { availableWallets.isNotEmpty() }
        walletId = availableWallets.first().id
    }
    
    private suspend fun createDid(didType: String): String {
        val did = walletClient.post("$walletUrl/wallet-api/wallet/$walletId/dids/create/$didType") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.bodyAsText()
        }
        println("did:$didType created, did = $did")
        assertNotNull(did)
        assertTrue(did.startsWith("did:$didType"))
        return did
    }
    
    private suspend fun createDids() {
        didMethodsToTest.forEach {
            println("\nUse Case -> Create a did:$it\n")
            createDid(it)
        }
    }
    
    private suspend fun testUserInfo() {
        println("\nUse Case -> User Info\n")
        val endpoint = "$walletUrl/wallet-api/auth/user-info"
        println("GET ($endpoint)")
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    private suspend fun testUserSession() {
        println("\nUse Case -> Session\n")
        val endpoint = "$walletUrl/wallet-api/auth/session"
        println("GET ($endpoint")
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    private suspend fun deleteCredential(credentialId: String) {
        println("\nUse Case -> Delete Credential\n")
        
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/$credentialId"
        println("DELETE ($endpoint")
        
        walletClient.delete(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.Accepted, response.status)
        }
    }
    
    private suspend fun viewCredential(credentialId: String) {
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials/$credentialId"
        println("GET ($endpoint")
        println("\nUse Case -> View Credential By Id\n")
        
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val vc = response.body<JsonObject>()["document"]?.jsonPrimitive?.content ?: error("No document found")
            println("Found Credential -> $vc")
        }
    }
    
    private suspend fun listCredentials(): JsonArray = run {
        getWallets()
        println("\nUse -> List credentials for wallet, id = $walletId\n")
        
        val endpoint = "$walletUrl/wallet-api/wallet/$walletId/credentials"
        
        println("GET $endpoint")
        walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()
        }
    }
    
    protected suspend fun listAllDids(): List<WalletDid> {
        println("Running DID listing...")
        val availableDids = walletClient.get("$walletUrl/wallet-api/wallet/$walletId/dids")
            .body<List<WalletDid>>()
        println("DID listing: $availableDids\n")
        
        assertTrue { availableDids.isNotEmpty() }
        firstDid = availableDids.first().did
        return availableDids
    }
    
    private suspend fun deleteAllDids(dids: List<WalletDid>) {
        println("\nUse Case -> Delete DIDs\n")
        
        dids.forEach {
            val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/${it.did}"
            println("DELETE $endpoint")
            walletClient.delete(endpoint) {
                bearerAuth(token)
            }.let { response ->
                assertEquals(HttpStatusCode.Accepted, response.status)
                println("DID deleted!")
            }
        }
    }
    
    private suspend fun testKeys() {
        println("\nUse Case -> List Keys\n")
        var endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys"
        println("GET $endpoint")
        val keys = walletClient.get(endpoint) {
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<JsonArray>()[0].jsonObject
        }
        val algorithm = keys["algorithm"]?.jsonPrimitive?.content
        assertEquals("Ed25519", algorithm)
        
        println("\nUse Case -> Generate new key of type RSA\n")
        endpoint = "$walletUrl/wallet-api/wallet/$walletId/keys/generate?type=RSA"
        println("POST $endpoint")
        walletClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    suspend fun testAuthenticationEndpoints(user: User) {
        getTokenFor(user)
        testUserInfo()
        testUserSession()
        getWallets()
    }
    
    suspend fun testCredentialEndpoints(user: User = defaultTestUser) {
        getTokenFor(user)
        getWallets()
        val response: JsonArray = listCredentials()
        assertNotEquals(response.size, 0)
        val id = response[0].jsonObject["id"]?.jsonPrimitive?.content ?: error("No credentials found")
        viewCredential(id)
        deleteCredential(id)
    }
    
    suspend fun testCredentialIssuance(user: User = defaultTestUser) {
        getTokenFor(user)
        getWallets()
        val offerUri = issueJwtCredential()
        println("offerUri = $offerUri")
        requestCredential(offerUri, firstDid)
    }
    
    suspend fun testDidsList(user: User = defaultTestUser) = run {
        getTokenFor(user)
        getWallets()
        println("\nUse Case -> List DIDs\n")
        println("Number of Dids found: ${listAllDids().size}")
    }
    
    suspend fun testDefaultDid(user: User = defaultTestUser) {
        getTokenFor(user)
        getWallets()
        println("\nUse Case -> Delete DIDs\n")
        
        listAllDids().let { dids ->
            assertNotEquals(0, dids.size)
            val defaultDid = dids[0]
            println("\nUse Case -> Set default did to $defaultDid\n")
            val endpoint = "$walletUrl/wallet-api/wallet/$walletId/dids/default?did=$defaultDid"
            println("POST $endpoint")
            walletClient.post(endpoint) {
                bearerAuth(token)
            }.let { response ->
                assertEquals(HttpStatusCode.Accepted, response.status)
            }
        }
    }
    
    suspend fun testDidsDelete(user: User = defaultTestUser) = run {
        getTokenFor(user)
        getWallets()
        println("\nUse Case -> Delete DIDs\n")
        listAllDids().let { dids ->
            println("Number of Dids found: ${dids.size}")
            dids.forEach {
                println(" DID: $it")
            }
            deleteAllDids(dids)
        }
    }
    
    suspend fun testDidsCreate(user: User = defaultTestUser) = run {
        getTokenFor(user)
        getWallets()
        println("\nUse Case -> Create DIDs\n")
        createDids()
    }
    
    suspend fun testKeyEndpoints(user: User = defaultTestUser) {
        getTokenFor(user)
        getWallets()
        testKeys()
    }
    
}
