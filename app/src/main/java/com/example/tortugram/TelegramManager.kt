package com.example.tortugram

import android.content.Context
import android.os.Build
import android.util.Log
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestión de Telegram mediante TDLib: autenticación, chats, mensajes, búsqueda, descargas,
 * almacenamiento y reportes.
 *
 * isaac-maker 2026
 */
data class ChatFolderItem(
    val id: Int,
    val title: String
)

/**
 * Resultado del buscador global, separado en tres fuentes para mostrarlas como secciones.
 */
data class GlobalSearchResults(
    val localChats: List<Chat> = emptyList(),
    val contacts: List<User> = emptyList(),
    val globalChats: List<Chat> = emptyList()
) {
    val isEmpty: Boolean
        get() = localChats.isEmpty() && contacts.isEmpty() && globalChats.isEmpty()
}

object TelegramManager {


    private const val TAG = "TelegramManager"

    private const val API_ID =  00000000
    private const val API_HASH = "5cabcdewfghtikills........"

    // Tamaño de página del historial.
    private const val MESSAGE_PAGE_SIZE = 100

    // Páginas consecutivas sin mensajes nuevos toleradas antes de dar por terminado el
    // historial.
    private const val MAX_STUCK_PAGES = 3

    private var client: TdlClient? = null
    private var dbPath: String = ""

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _qrCodeLink = MutableStateFlow<String?>(null)
    val qrCodeLink: StateFlow<String?> = _qrCodeLink.asStateFlow()

    private val _isPasswordRequired = MutableStateFlow(false)
    val isPasswordRequired: StateFlow<Boolean> = _isPasswordRequired.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Usuario con sesión iniciada (solo lectura desde la UI).
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _folders =
        MutableStateFlow<List<ChatFolderItem>>(
            listOf(ChatFolderItem(0, "All"))
        )

    val folders: StateFlow<List<ChatFolderItem>> = _folders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow(0)
    val selectedFolderId: StateFlow<Int> = _selectedFolderId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Estado de paginación: chat actual, mensaje más antiguo cargado, existencia de más
    // mensajes y bloqueo de cargas simultáneas.
    @Volatile private var currentChatId: Long? = null
    @Volatile private var oldestMessageId: Long = 0L
    @Volatile private var hasMoreMessages: Boolean = true
    @Volatile private var loadingMessages: Boolean = false

    // Se incrementa al abrir un chat; las cargas de generaciones anteriores se descartan.
    @Volatile private var loadGeneration: Int = 0
    @Volatile private var stuckPages: Int = 0

    // Observables de la UI: estado de carga y chat cuyo historial llegó al inicio.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _endReachedChatId = MutableStateFlow<Long?>(null)
    val endReachedChatId: StateFlow<Long?> = _endReachedChatId.asStateFlow()

    // Buscador global (HomeScreen); solo lectura.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(GlobalSearchResults())
    val searchResults: StateFlow<GlobalSearchResults> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun initClient(context: Context) {
        if (client != null) return

        scope.launch(Dispatchers.IO) {
            dbPath = context.filesDir.absolutePath + "/tdlib"
            File(dbPath).mkdirs()

            Log.d(TAG, "Iniciando cliente TDLib...")

            val newClient = TdlClient.create()
            client = newClient

            sendParameters()

            newClient.allUpdates.collect { update ->

                when (update) {

                    is UpdateAuthorizationState -> {
                        handleAuthState(update.authorizationState)
                    }

                    is UpdateNewChat -> {

                        val currentList = _chats.value.toMutableList()

                        val index =
                            currentList.indexOfFirst {
                                it.id == update.chat.id
                            }

                        if (index != -1) {
                            currentList[index] = update.chat
                        } else {
                            currentList.add(update.chat)
                        }

                        _chats.value = currentList
                    }

                    is UpdateChatTitle -> {

                        scope.launch(Dispatchers.IO) {

                            val res =
                                client?.getChat(update.chatId)

                            if (res is TdlResult.Success<*>) {

                                val updatedChat =
                                    res.result as? Chat

                                if (updatedChat != null) {

                                    val currentList =
                                        _chats.value.toMutableList()

                                    val index =
                                        currentList.indexOfFirst {
                                            it.id == update.chatId
                                        }

                                    if (index != -1) {

                                        currentList[index] =
                                            updatedChat

                                        _chats.value =
                                            currentList
                                    }
                                }
                            }
                        }
                    }

                    is UpdateChatFolders -> {

                        val folderList =
                            mutableListOf(
                                ChatFolderItem(0, "All")
                            )

                        update.chatFolders.forEach { folderInfo ->

                            val folderTitle =
                                folderInfo.name.text.text

                            folderList.add(
                                ChatFolderItem(
                                    folderInfo.id,
                                    folderTitle
                                )
                            )
                        }

                        _folders.value = folderList
                    }

                    else -> {}
                }
            }
        }
    }

    private fun sendParameters() {

        scope.launch(Dispatchers.IO) {

            val res =
                client?.setTdlibParameters(

                    useTestDc = false,

                    databaseDirectory = dbPath,

                    filesDirectory = "$dbPath/files",

                    databaseEncryptionKey =
                        ByteArray(0),

                    useFileDatabase = true,

                    useChatInfoDatabase = true,

                    useMessageDatabase = true,

                    useSecretChats = false,

                    apiId = API_ID,

                    apiHash = API_HASH,

                    systemLanguageCode = "en",

                    deviceModel = Build.MODEL,

                    systemVersion =
                        Build.VERSION.RELEASE,

                    applicationVersion = "1.0"
                )

            Log.d(
                TAG,
                "Resultado setTdlibParameters: $res"
            )
        }
    }

    private fun handleAuthState(
        authState: AuthorizationState
    ) {

        Log.d(
            TAG,
            "Estado de Autorización: ${
                authState::class.java.simpleName
            }"
        )

        when (authState) {

            is AuthorizationStateWaitTdlibParameters -> {
                sendParameters()
            }

            is AuthorizationStateWaitPhoneNumber -> {

                scope.launch(Dispatchers.IO) {

                    Log.d(
                        TAG,
                        "Pidiendo QR..."
                    )

                    val res =
                        client?.requestQrCodeAuthentication(
                            otherUserIds = LongArray(0)
                        )

                    Log.d(
                        TAG,
                        "Resultado petición QR: $res"
                    )
                }
            }

            is AuthorizationStateWaitOtherDeviceConfirmation -> {

                Log.d(
                    TAG,
                    "QR Enlace listo: ${authState.link}"
                )

                _isPasswordRequired.value = false

                _qrCodeLink.value =
                    authState.link
            }

            is AuthorizationStateWaitPassword -> {

                Log.d(
                    TAG,
                    "Se requiere contraseña de 2FA"
                )

                _qrCodeLink.value = null

                _isPasswordRequired.value = true
            }

            is AuthorizationStateReady -> {

                Log.d(
                    TAG,
                    "Sesión lista!"
                )

                _qrCodeLink.value = null

                _isPasswordRequired.value = false

                _isLoggedIn.value = true

                // Reasigna el idioma de la cuenta, ya que systemLanguageCode solo aplica en el
                // primer inicio de sesión.
                scope.launch(Dispatchers.IO) {
                    val res = client?.setOption(
                        name = "language_pack_id",
                        value = OptionValueString(value = "en")
                    )
                    Log.d(TAG, "setOption(language_pack_id=en) -> $res")
                }

                loadCurrentUser()

                loadChats()
            }

            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosed -> {

                _isLoggedIn.value = false

                _isPasswordRequired.value = false

                _qrCodeLink.value = null

                _currentUser.value = null

                client = null
            }

            else -> {}
        }
    }

    /**
     * Obtiene nombre y foto del usuario con sesión iniciada; se invoca desde handleAuthState.
     */
    private fun loadCurrentUser() {

        scope.launch(Dispatchers.IO) {

            val res = client?.getMe()

            if (res is TdlResult.Success<*>) {
                _currentUser.value = res.result as? User
            }
        }
    }

    fun checkPassword(
        password: String,
        onResult: (Boolean) -> Unit
    ) {

        scope.launch(Dispatchers.IO) {

            val res =
                client?.checkAuthenticationPassword(
                    password = password
                )

            if (res is TdlResult.Success<*>) {

                _isPasswordRequired.value = false

                _isLoggedIn.value = true

                loadChats()

                withContext(Dispatchers.Main) {
                    onResult(true)
                }

            } else {

                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun downloadFile(
        fileId: Int,
        onComplete: (String) -> Unit
    ) {

        scope.launch(Dispatchers.IO) {

            val result =
                client?.downloadFile(

                    fileId = fileId,

                    priority = 3,

                    offset = 0,

                    limit = 0,

                    synchronous = true
                )

            if (result is TdlResult.Success<*>) {

                val file =
                    result.result
                            as? dev.g000sha256.tdl.dto.File

                val path =
                    file?.local?.path ?: ""

                if (path.isNotEmpty()) {

                    withContext(Dispatchers.Main) {
                        onComplete(path)
                    }
                }
            }

            // Notifica a StorageManager para aplicar el límite de almacenamiento.
            StorageManager.onFileDownloaded()
        }
    }

    // Job de la carga de chats en curso; permite cancelarla al cambiar de carpeta.
    private var loadChatsJob: Job? = null

    fun selectFolder(folderId: Int) {

        _selectedFolderId.value = folderId

        _chats.value = emptyList()

        loadChats(folderId)
    }

    fun loadChats(
        folderId: Int = _selectedFolderId.value
    ) {

        loadChatsJob?.cancel()

        loadChatsJob = scope.launch(Dispatchers.IO) {

            val targetChatList: ChatList =
                if (folderId == 0) {
                    ChatListMain()
                } else {
                    ChatListFolder(
                        chatFolderId = folderId
                    )
                }

            var lastLoadedCount = -1

            while (true) {

                // Primero se lee la caché con getChats(); un fallo de loadChats() es normal y
                // no impide mostrar lo obtenido.
                val chatsResult =
                    client?.getChats(
                        chatList = targetChatList,
                        limit = 500
                    )

                if (chatsResult is TdlResult.Success<*>) {

                    val chatsObj =
                        chatsResult.result as? Chats

                    val ids =
                        chatsObj?.chatIds
                            ?: LongArray(0)

                    val deferredChats =
                        ids.map { id ->

                            async {

                                val c =
                                    client?.getChat(
                                        chatId = id
                                    )

                                if (
                                    c is TdlResult.Success<*>
                                ) {
                                    c.result as? Chat
                                } else {
                                    null
                                }
                            }
                        }

                    val fullChats =
                        deferredChats
                            .awaitAll()
                            .filterNotNull()

                    // Descarta el resultado si la carpeta cambió.
                    if (_selectedFolderId.value != folderId) {
                        return@launch
                    }

                    _chats.value = fullChats

                    if (ids.size == lastLoadedCount) {
                        // Sin chats nuevos: la carga está completa.
                        break
                    }

                    lastLoadedCount = ids.size
                }

                val loadRes =
                    client?.loadChats(
                        chatList = targetChatList,
                        limit = 100
                    )

                if (loadRes !is TdlResult.Success<*>) {
                    // Fin normal de la lista de chats.
                    break
                }
            }
        }
    }

    /**
     * Inicia o reinicia la carga del historial de un chat, invalidando cualquier carga en
     * curso.
     */
    fun loadMessages(chatId: Long) {

        loadGeneration++

        currentChatId = chatId
        oldestMessageId = 0L
        hasMoreMessages = true
        stuckPages = 0
        loadingMessages = false

        _isLoading.value = false
        _endReachedChatId.value = null
        _messages.value = emptyList()

        loadMoreMessages(chatId)
    }


    fun loadMoreMessages(chatId: Long? = null) {

        val targetChatId =
            chatId ?: currentChatId ?: return

        if (loadingMessages) {
            return
        }

        if (!hasMoreMessages) {
            return
        }

        if (currentChatId != targetChatId) {
            return
        }

        loadingMessages = true
        _isLoading.value = true

        val generation = loadGeneration

        scope.launch(Dispatchers.IO) {

            try {

                val result =
                    client?.getChatHistory(
                        chatId = targetChatId,
                        fromMessageId = oldestMessageId,
                        offset = 0,
                        limit = MESSAGE_PAGE_SIZE,
                        onlyLocal = false
                    )

                // Descarta la respuesta si el chat cambió.
                if (generation != loadGeneration) {
                    return@launch
                }

                if (result is TdlResult.Success<*>) {

                    val messagesResult =
                        result.result as? Messages

                    val page =
                        messagesResult
                            ?.messages
                            ?.filterNotNull()
                            ?: emptyList()

                    val existingIds =
                        _messages.value
                            .asSequence()
                            .map { it.id }
                            .toHashSet()

                    val newMessages =
                        page.filter {
                            it.id !in existingIds
                        }

                    if (newMessages.isNotEmpty()) {

                        stuckPages = 0

                        _messages.value =
                            _messages.value + newMessages

                        oldestMessageId =
                            page.minOf { it.id }

                    } else {

                        // Una página vacía no siempre indica el fin del historial; se reintenta
                        // varias veces.
                        stuckPages++

                        if (stuckPages >= MAX_STUCK_PAGES) {

                            hasMoreMessages = false
                            _endReachedChatId.value = targetChatId

                        } else {

                            delay(400)
                        }
                    }

                } else {

                    // Espera antes de reintentar tras un fallo.
                    delay(1500)
                }

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error cargando historial del chat $targetChatId",
                    exception
                )

                delay(1500)

            } finally {

                // Libera el bloqueo solo si la generación sigue vigente.
                if (generation == loadGeneration) {
                    loadingMessages = false
                    _isLoading.value = false
                }
            }
        }
    }

    fun hasMoreMessages(): Boolean {
        return hasMoreMessages
    }

    fun isLoadingMessages(): Boolean {
        return loadingMessages
    }

    // Buscador global: combina en paralelo searchChats y searchContacts (locales) y
    // searchPublicChats (servidores de Telegram), sin modificar _chats ni _messages. Las firmas
    // dependen de la versión de tdl-coroutines.

    private const val SEARCH_DEBOUNCE_MS = 300L
    private const val SEARCH_LOCAL_LIMIT = 30
    private const val SEARCH_CONTACTS_LIMIT = 30

    fun searchGlobal(query: String) {

        _searchQuery.value = query

        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = GlobalSearchResults()
            _isSearching.value = false
            return
        }

        searchJob = scope.launch(Dispatchers.IO) {

            // Debounce para no consultar a TDLib por cada letra.
            delay(SEARCH_DEBOUNCE_MS)

            _isSearching.value = true

            try {

                val localChatsDeferred = async {
                    val res = client?.searchChats(
                        query = query,
                        limit = SEARCH_LOCAL_LIMIT
                    )
                    val chatsObj =
                        (res as? TdlResult.Success<*>)?.result as? Chats

                    val ids = chatsObj?.chatIds ?: LongArray(0)

                    ids.map { id ->
                        async {
                            val c = client?.getChat(chatId = id)
                            if (c is TdlResult.Success<*>) {
                                c.result as? Chat
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val contactsDeferred = async {
                    val res = client?.searchContacts(
                        query = query,
                        limit = SEARCH_CONTACTS_LIMIT
                    )
                    val usersObj =
                        (res as? TdlResult.Success<*>)?.result as? Users

                    val ids = usersObj?.userIds ?: LongArray(0)

                    ids.map { id ->
                        async {
                            val u = client?.getUser(userId = id)
                            if (u is TdlResult.Success<*>) {
                                u.result as? User
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val globalChatsDeferred = async {
                    val res = client?.searchPublicChats(query = query)
                    val chatsObj =
                        (res as? TdlResult.Success<*>)?.result as? Chats

                    val ids = chatsObj?.chatIds ?: LongArray(0)

                    ids.map { id ->
                        async {
                            val c = client?.getChat(chatId = id)
                            if (c is TdlResult.Success<*>) {
                                c.result as? Chat
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val localChats = localChatsDeferred.await()
                val contacts = contactsDeferred.await()
                val globalChats = globalChatsDeferred.await()

                // Descarta la respuesta si la consulta cambió.
                if (_searchQuery.value != query) {
                    return@launch
                }

                // Excluye de «Global» los chats ya listados en «Chats».
                val localIds = localChats.map { it.id }.toHashSet()
                val dedupedGlobalChats =
                    globalChats.filter { it.id !in localIds }

                // Excluye de «Contactos» a quienes ya tienen chat en «Chats».
                val localPrivateUserIds = localChats
                    .mapNotNull { (it.type as? ChatTypePrivate)?.userId }
                    .toHashSet()
                val dedupedContacts =
                    contacts.filter { it.id !in localPrivateUserIds }

                _searchResults.value = GlobalSearchResults(
                    localChats = localChats,
                    contacts = dedupedContacts,
                    globalChats = dedupedGlobalChats
                )

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error en búsqueda global: \"$query\"",
                    exception
                )

            } finally {

                if (_searchQuery.value == query) {
                    _isSearching.value = false
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = GlobalSearchResults()
        _isSearching.value = false
    }

    /**
     * Abre o crea el chat privado con un contacto y devuelve su chatId.
     */
    fun openPrivateChat(userId: Long, onResult: (Long?) -> Unit) {

        scope.launch(Dispatchers.IO) {

            val res = client?.createPrivateChat(
                userId = userId,
                force = true
            )

            val chat =
                (res as? TdlResult.Success<*>)?.result as? Chat

            withContext(Dispatchers.Main) {
                onResult(chat?.id)
            }
        }
    }


    fun streamVideo(fileId: Int) {

        scope.launch(Dispatchers.IO) {

            client?.downloadFile(

                fileId = fileId,

                priority = 1,

                offset = 0,

                limit = 0,

                synchronous = false
            )
        }
    }


    fun prefetchRange(fileId: Int, offset: Long, length: Long) {

        scope.launch(Dispatchers.IO) {

            try {

                client?.downloadFile(

                    fileId = fileId,

                    priority = 16,

                    offset = offset,

                    limit = length,

                    synchronous = false
                )

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error en prefetch de fileId=$fileId offset=$offset",
                    exception
                )
            }
        }
    }

    suspend fun downloadRange(
        fileId: Int,
        offset: Long,
        length: Long
    ): dev.g000sha256.tdl.dto.File? {

        val result =
            client?.downloadFile(

                fileId = fileId,

                priority = 32,

                offset = offset,

                limit = length,

                synchronous = true
            )

        // El streaming es la principal fuente de crecimiento del almacenamiento; se notifica a
        // StorageManager.
        StorageManager.onFileDownloaded()

        return if (
            result is TdlResult.Success<*>
        ) {

            result.result
                    as? dev.g000sha256.tdl.dto.File

        } else {
            null
        }
    }

    // Almacenamiento (StorageScreen / StorageManager): usa exclusivamente funciones de TDLib,
    // sin afectar la base de datos ni la sesión.

    /**
     * Estadísticas de espacio de TDLib: archivos descargados (filesSize) y base de datos
     * (databaseSize).
     */
    suspend fun getStorageStatisticsFast(): StorageInfo {

        val result = client?.getStorageStatisticsFast()

        val stats =
            (result as? TdlResult.Success<*>)
                ?.result as? dev.g000sha256.tdl.dto.StorageStatisticsFast

        return StorageInfo(
            filesSize = stats?.filesSize ?: 0L,
            databaseSize = stats?.databaseSize ?: 0L,
            fileCount = stats?.fileCount ?: 0
        )
    }

    /**
     * Solicita a TDLib recortar los archivos descargados hasta [maxTotalSizeBytes] (0 = todo lo
     * descargable). No afecta la base de datos ni la autenticación. La firma depende de la
     * versión de tdl-coroutines.
     */
    suspend fun optimizeStorage(maxTotalSizeBytes: Long): Long {

        val result = client?.optimizeStorage(
            size = maxTotalSizeBytes,
            ttl = 0,
            count = 0,
            immunityDelay = 0,
            fileTypes = emptyArray(),
            chatIds = LongArray(0),
            excludeChatIds = LongArray(0),
            returnDeletedFileStatistics = false,
            chatLimit = 0
        )

        val stats =
            (result as? TdlResult.Success<*>)
                ?.result as? dev.g000sha256.tdl.dto.StorageStatistics

        return stats?.size ?: 0L
    }

    // Cierre de sesión real en el servidor de Telegram: limpia el estado en memoria y reinicia
    // el cliente TDLib para el próximo login por QR.
    fun logOut(context: Context, onComplete: () -> Unit) {

        scope.launch(Dispatchers.IO) {

            client?.logOut()

            // Espera la confirmación del cierre (AuthorizationStateClosed).
            var waited = 0
            while (client != null && waited < 5000) {
                delay(100)
                waited += 100
            }

            _currentUser.value = null
            _chats.value = emptyList()
            _folders.value = listOf(ChatFolderItem(0, "All"))
            _selectedFolderId.value = 0
            _messages.value = emptyList()
            _searchQuery.value = ""
            _searchResults.value = GlobalSearchResults()
            _isSearching.value = false
            currentChatId = null
            oldestMessageId = 0L
            hasMoreMessages = true
            loadGeneration++

            withContext(Dispatchers.Main) {
                // Inicia un cliente nuevo para el próximo login por QR.
                initClient(context)
                onComplete()
            }
        }
    }

    // Reporte de chats a Telegram (ReportScreen): reportChat opera por pasos con un optionId y
    // puede devolver Ok, un submenú (OptionRequired) o solicitar texto (TextRequired). Los
    // tipos de los parámetros dependen de la versión de la librería.

    data class ReportOptionItem(
        val id: ByteArray,
        val text: String
    )

    sealed class ReportStepResult {
        object Ok : ReportStepResult()
        data class ChooseOption(val title: String, val options: List<ReportOptionItem>) : ReportStepResult()
        data class NeedsText(val optionId: ByteArray, val isOptional: Boolean) : ReportStepResult()
        data class Failed(val message: String? = null) : ReportStepResult()
    }

    /**
     * Un paso del reporte: se inicia con optionId vacío y se repite con la opción elegida
     * (ChooseOption) o el texto pedido (NeedsText) hasta obtener Ok.
     */
    suspend fun reportChat(
        chatId: Long,
        optionId: ByteArray = ByteArray(0),
        text: String = ""
    ): ReportStepResult {

        return try {

            val result = client?.reportChat(
                chatId = chatId,
                messageIds = LongArray(0),
                optionId = optionId,
                text = text
            )

            Log.d(TAG, "reportChat($chatId) -> $result")

            if (result !is TdlResult.Success<*>) {
                Log.e(TAG, "reportChat($chatId) falló: $result")
                return ReportStepResult.Failed(result?.toString())
            }

            when (val reportResult = result.result as? ReportChatResult) {

                is ReportChatResultOk ->
                    ReportStepResult.Ok

                is ReportChatResultOptionRequired ->
                    ReportStepResult.ChooseOption(
                        title = reportResult.title,
                        options = reportResult.options.map {
                            ReportOptionItem(it.id, it.text)
                        }
                    )

                is ReportChatResultTextRequired ->
                    ReportStepResult.NeedsText(
                        optionId = reportResult.optionId,
                        isOptional = reportResult.isOptional
                    )

                else -> {
                    // Cubre reportChatResultMessagesRequired y tipos nuevos.
                    Log.e(TAG, "reportChat($chatId): resultado no manejado: $reportResult")
                    ReportStepResult.Failed(reportResult?.toString())
                }
            }

        } catch (exception: Exception) {
            Log.e(TAG, "reportChat($chatId) lanzó una excepción", exception)
            ReportStepResult.Failed(exception.message)
        }
    }
}

data class StorageInfo(
    val filesSize: Long = 0L,
    val databaseSize: Long = 0L,
    val fileCount: Int = 0
)
