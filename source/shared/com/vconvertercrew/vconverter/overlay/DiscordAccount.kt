package com.vconvertercrew.vconverter.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.online.Discord
import com.vconvertercrew.vconverter.online.DiscordProfile
import com.vconvertercrew.vconverter.online.Online
import com.vconvertercrew.vconverter.online.OnlineApi
import com.vconvertercrew.vconverter.online.OnlineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

object DiscordAccount {
    var profile by mutableStateOf<DiscordProfile?>(null)
        private set
    var avatar by mutableStateOf<ImageBitmap?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set

    private var store: (String) -> Unit = {}
    private var decode: (ByteArray) -> ImageBitmap? = { null }
    private val listeners = ArrayList<(DiscordProfile?) -> Unit>()
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    private var job: Job? = null
    private var attempts = 0
    private var avatarJob: Job? = null

    fun setup(saved: String?, store: (String) -> Unit, decode: (ByteArray) -> ImageBitmap?) {
        this.store = store
        this.decode = decode
        profile = DiscordProfile.parse(saved)
        loadAvatar()
        notifyListeners()
    }

    fun listen(listener: (DiscordProfile?) -> Unit) {
        listeners += listener
        listener(profile)
    }

    fun login(open: (String) -> Unit) {
        if (busy) {
            cancel()
            status = "Login cancelled."
            return
        }
        if (!Online.isConfigured) {
            status = "Set the Online Server in Preferences first."
            return
        }
        busy = true
        status = "Opening Discord..."
        val attempt = ++attempts
        job = scope.launch {
            try {
                val api = Online.api()
                val ticket = withContext(Dispatchers.Default) { api.discordLogin() }
                status = if (runCatching { open(ticket.url) }.isSuccess) "Finish the login in your browser, then come back here."
                else "Open this link in your browser: ${ticket.url}"
                val found = waitFor(api, ticket.id)
                profile = found
                runCatching { store(found.toJson()) }
                status = "Logged in as ${found.name}."
                loadAvatar()
                notifyListeners()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "The Discord login failed."
            } finally {
                if (attempt == attempts) {
                    busy = false
                    job = null
                }
            }
        }
    }

    private suspend fun waitFor(api: OnlineApi, id: String): DiscordProfile {
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() < 10.minutes) {
            delay(2000)
            withContext(Dispatchers.Default) { api.discordLoginStatus(id) }?.let { return it }
        }
        throw OnlineException("The Discord login timed out. Try again.")
    }

    fun logout() {
        cancel()
        profile = null
        avatar = null
        runCatching { store("") }
        status = "Logged out of Discord."
        notifyListeners()
    }

    private fun cancel() {
        attempts++
        job?.cancel()
        job = null
        busy = false
    }

    private fun notifyListeners() {
        val current = profile
        listeners.forEach { runCatching { it(current) } }
    }

    private fun loadAvatar() {
        avatarJob?.cancel()
        val url = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: run {
            avatar = null
            return
        }
        avatarJob = scope.launch {
            val bytes = withContext(Dispatchers.Default) { Discord.download(url) }
            avatar = bytes?.let { runCatching { decode(it) }.getOrNull() }
        }
    }
}

@Composable
fun DiscordBadge(modifier: Modifier = Modifier) {
    val profile = DiscordAccount.profile ?: return
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatar = DiscordAccount.avatar
        if (avatar != null) {
            Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF5865F2)), contentAlignment = Alignment.Center) {
                Text(profile.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            profile.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}

fun discordOption(open: (String) -> Unit): MenuOption {
    val profile = DiscordAccount.profile
    val hint = when {
        DiscordAccount.busy -> "${DiscordAccount.status} Enter cancels."
        profile != null -> "Logged in as ${profile.name} (@${profile.username}). Enter logs out."
        else -> DiscordAccount.status.ifBlank { "Log in with Discord to show your name and avatar in V-Converter and in your Discord status. The Online Server does the login." }
    }
    return when {
        DiscordAccount.busy -> MenuOption.Action("Waiting For Discord", hint) { DiscordAccount.login(open) }
        profile != null -> MenuOption.Action("Logout Of Discord", hint) { DiscordAccount.logout() }
        else -> MenuOption.Action("Login To Discord", hint) { DiscordAccount.login(open) }
    }
}
