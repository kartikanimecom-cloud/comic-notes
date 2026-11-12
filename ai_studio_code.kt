package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// 1. DATA MODELS & ENUMS
// ==========================================
enum class ComicGenre(val displayName: String, val iconName: String, val badgeColor: Long) {
    SUPERHERO("Superhero Action", "⚡", 0xFFE53935),
    MANGA_SHONEN("Manga Shonen", "🔥", 0xFFFF9800),
    DETECTIVE_NOIR("Detective Noir", "🕵️", 0xFF424242),
    SCI_FI("Sci-Fi Galaxy", "🚀", 0xFF00ACC1),
    SLICE_OF_LIFE("Slice of Life", "☕", 0xFF43A047),
    HISTORICAL_EPIC("Historical Epic", "🏛️", 0xFF8D6E63)
}

data class ComicCharacter(
    val id: String,
    val name: String,
    val role: String,
    val avatarType: String = "HERO",
    val colorHex: String = "#FF5722",
    val catchphrase: String = ""
)

data class SpeechBubble(
    val speaker: String,
    val text: String,
    val bubbleType: String = "SPEECH",
    val position: String = "TOP_LEFT"
)

data class SoundEffect(
    val sound: String,
    val style: String = "EXPLOSION",
    val colorHex: String = "#FFEB3B"
)

data class ComicPanel(
    val panelNumber: Int,
    val caption: String,
    val layoutType: String = "SQUARE",
    val sceneDescription: String,
    val charactersPresent: List<String> = emptyList(),
    val dialogues: List<SpeechBubble> = emptyList(),
    val soundEffect: SoundEffect? = null,
    val visualMood: String = "DRAMATIC_BURST",
    val accentColor: String = "#FFC107"
)

data class ComicStrip(
    val id: Long = 0,
    val title: String,
    val issueNumber: String = "#1",
    val genre: ComicGenre,
    val originalNote: String,
    val summary: String,
    val tagline: String,
    val characters: List<ComicCharacter>,
    val panels: List<ComicPanel>,
    val createdAt: Long = System.currentTimeMillis()
)

data class SampleNote(
    val title: String,
    val category: String,
    val icon: String,
    val suggestedGenre: ComicGenre,
    val noteContent: String
)

object SampleNotesRepository {
    val samples = listOf(
        SampleNote(
            title = "Mitochondria: Powerhouse of the Cell",
            category = "Biology",
            icon = "🧬",
            suggestedGenre = ComicGenre.SUPERHERO,
            noteContent = """
                Topic: Cellular Respiration & Mitochondria
                - Mitochondria are double-membraned organelles called the "powerhouse of the cell".
                - Inside the matrix and inner cristae, glucose breakdown products and oxygen enter the Krebs Cycle and Electron Transport Chain.
                - They synthesize ATP (Adenosine Triphosphate), the universal energy currency for all cellular tasks like muscle contraction and thinking.
                - Without ATP, the cell shuts down. When energy demand spikes, mitochondria work in overdrive!
            """.trimIndent()
        ),
        SampleNote(
            title = "Apollo 11: The Moon Landing (1969)",
            category = "History",
            icon = "🚀",
            suggestedGenre = ComicGenre.SCI_FI,
            noteContent = """
                Subject: Apollo 11 Mission (July 20, 1969)
                - Commander Neil Armstrong and Lunar Module Pilot Buzz Aldrin flew the Eagle lunar lander down to the Moon's Sea of Tranquility.
                - Michael Collins orbited above in Columbia.
                - Fuel was dangerously low (less than 30 seconds remaining) as Armstrong steered away from boulder-strewn craters.
                - Touchdown! Armstrong radioed: "Houston, Tranquility Base here. The Eagle has landed."
                - Armstrong descended the ladder: "That's one small step for man, one giant leap for mankind."
            """.trimIndent()
        ),
        SampleNote(
            title = "How DNS & Internet Packets Work",
            category = "Technology",
            icon = "💻",
            suggestedGenre = ComicGenre.DETECTIVE_NOIR,
            noteContent = """
                Tech Note: The Journey of an HTTP Web Request
                - A user types 'google.com' into the browser.
                - The browser asks the Local DNS Resolver: 'Who has this IP address?'
                - The DNS Resolver races through Root Servers, TLD servers (.com), and Authoritative Nameservers like an undercover detective finding a phone number.
                - Once resolved to an IP address (like 142.250.190.46), data is sliced into TCP/IP packets and routed through routers across undersea cables.
                - The server receives the packets, responds with HTML/CSS, and the browser paints the webpage in milliseconds!
            """.trimIndent()
        ),
        SampleNote(
            title = "Newton & The Falling Apple",
            category = "Physics",
            icon = "🍎",
            suggestedGenre = ComicGenre.MANGA_SHONEN,
            noteContent = """
                Physics Lecture: Law of Universal Gravitation
                - Year 1665: Cambridge was closed due to the Great Plague. Isaac Newton studied at his family home in Woolsthorpe.
                - While sitting under an apple tree in the garden, he watched an apple drop straight to the ground.
                - He pondered: Why does the apple always fall perpendicular to the earth, not sideways or upwards?
                - There must be an attractive force! Could this same force extend all the way to the Moon to keep it in orbit around the Earth?
                - Result: Every mass attracts every other mass with a force proportional to the product of their masses and inversely proportional to the square of the distance (F = G * (m1*m2)/r^2).
            """.trimIndent()
        ),
        SampleNote(
            title = "The Morning Coffee Chronicles",
            category = "Daily Life",
            icon = "☕",
            suggestedGenre = ComicGenre.SLICE_OF_LIFE,
            noteContent = """
                Morning Routine Log:
                - 7:00 AM: The evil alarm clock rings like a siren. The protagonist snoozes it three times in sheer denial.
                - 7:30 AM: Finally rolling out of bed in zombie mode, wandering through the hallway.
                - 7:35 AM: Approaching the sacred Espresso Machine. Grinding fresh dark roast beans, tamping the portafilter, and pulling a double shot.
                - 7:40 AM: First sip of steaming espresso hits the bloodstream. Brain synapses fire up, eyes snap open, and productivity level jumps from 0% to 100%!
            """.trimIndent()
        )
    )
}

// ==========================================
// 2. ROOM DATABASE PERSISTENCE & CONVERTERS
// ==========================================
class ComicConverters {
    @TypeConverter fun fromGenre(genre: ComicGenre): String = genre.name
    @TypeConverter fun toGenre(name: String): ComicGenre = try { ComicGenre.valueOf(name) } catch (_: Exception) { ComicGenre.SUPERHERO }

    @TypeConverter
    fun fromCharacterList(characters: List<ComicCharacter>): String {
        val array = JSONArray()
        characters.forEach { char ->
            val obj = JSONObject()
            obj.put("id", char.id)
            obj.put("name", char.name)
            obj.put("role", char.role)
            obj.put("avatarType", char.avatarType)
            obj.put("colorHex", char.colorHex)
            obj.put("catchphrase", char.catchphrase)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toCharacterList(jsonStr: String?): List<ComicCharacter> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComicCharacter>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ComicCharacter(
                        id = obj.optString("id", "c_$i"),
                        name = obj.optString("name", "Character $i"),
                        role = obj.optString("role", "Protagonist"),
                        avatarType = obj.optString("avatarType", "HERO"),
                        colorHex = obj.optString("colorHex", "#FF5722"),
                        catchphrase = obj.optString("catchphrase", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @TypeConverter
    fun fromPanelList(panels: List<ComicPanel>): String {
        val array = JSONArray()
        panels.forEach { panel ->
            val pObj = JSONObject()
            pObj.put("panelNumber", panel.panelNumber)
            pObj.put("caption", panel.caption)
            pObj.put("layoutType", panel.layoutType)
            pObj.put("sceneDescription", panel.sceneDescription)
            pObj.put("visualMood", panel.visualMood)
            pObj.put("accentColor", panel.accentColor)

            val charsArr = JSONArray()
            panel.charactersPresent.forEach { charsArr.put(it) }
            pObj.put("charactersPresent", charsArr)

            val dialoguesArr = JSONArray()
            panel.dialogues.forEach { d ->
                val dObj = JSONObject()
                dObj.put("speaker", d.speaker)
                dObj.put("text", d.text)
                dObj.put("bubbleType", d.bubbleType)
                dObj.put("position", d.position)
                dialoguesArr.put(dObj)
            }
            pObj.put("dialogues", dialoguesArr)

            if (panel.soundEffect != null) {
                val sObj = JSONObject()
                sObj.put("sound", panel.soundEffect.sound)
                sObj.put("style", panel.soundEffect.style)
                sObj.put("colorHex", panel.soundEffect.colorHex)
                pObj.put("soundEffect", sObj)
            }
            array.put(pObj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toPanelList(jsonStr: String?): List<ComicPanel> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComicPanel>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val charsArr = obj.optJSONArray("charactersPresent")
                val chars = mutableListOf<String>()
                if (charsArr != null) {
                    for (c in 0 until charsArr.length()) { chars.add(charsArr.getString(c)) }
                }
                val dialoguesArr = obj.optJSONArray("dialogues")
                val dialogues = mutableListOf<SpeechBubble>()
                if (dialoguesArr != null) {
                    for (d in 0 until dialoguesArr.length()) {
                        val dObj = dialoguesArr.getJSONObject(d)
                        dialogues.add(
                            SpeechBubble(
                                speaker = dObj.optString("speaker", ""),
                                text = dObj.optString("text", ""),
                                bubbleType = dObj.optString("bubbleType", "SPEECH"),
                                position = dObj.optString("position", "TOP_LEFT")
                            )
                        )
                    }
                }
                val soundObj = obj.optJSONObject("soundEffect")
                val soundEffect = if (soundObj != null) {
                    SoundEffect(
                        sound = soundObj.optString("sound", "POW!"),
                        style = soundObj.optString("style", "IMPACT"),
                        colorHex = soundObj.optString("colorHex", "#FFEB3B")
                    )
                } else null

                list.add(
                    ComicPanel(
                        panelNumber = obj.optInt("panelNumber", i + 1),
                        caption = obj.optString("caption", ""),
                        layoutType = obj.optString("layoutType", "SQUARE"),
                        sceneDescription = obj.optString("sceneDescription", ""),
                        charactersPresent = chars,
                        dialogues = dialogues,
                        soundEffect = soundEffect,
                        visualMood = obj.optString("visualMood", "DRAMATIC_BURST"),
                        accentColor = obj.optString("accentColor", "#FFC107")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}

@Entity(tableName = "comics")
data class ComicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val issueNumber: String,
    val genre: ComicGenre,
    val originalNote: String,
    val summary: String,
    val tagline: String,
    val characters: List<ComicCharacter>,
    val panels: List<ComicPanel>,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ComicStrip = ComicStrip(id, title, issueNumber, genre, originalNote, summary, tagline, characters, panels, createdAt)
    companion object {
        fun fromDomain(s: ComicStrip): ComicEntity = ComicEntity(s.id, s.title, s.issueNumber, s.genre, s.originalNote, s.summary, s.tagline, s.characters, s.panels, s.createdAt)
    }
}

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY createdAt DESC")
    fun getAllComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicById(id: Long): ComicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: ComicEntity): Long

    @Update
    suspend fun updateComic(comic: ComicEntity)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ComicEntity::class], version = 1, exportSchema = false)
@TypeConverters(ComicConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comic_notes_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 3. GEMINI AI COMIC GENERATION SERVICE
// ==========================================
class ComicGeneratorService {
    private val comicResponseSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Catchy comic book title based on the notes"),
            "issueNumber" to Schema.string(description = "Comic issue label, e.g. 'Issue #1'"),
            "genre" to Schema.string(description = "Comic genre"),
            "summary" to Schema.string(description = "Brief 1-sentence recap"),
            "tagline" to Schema.string(description = "Dramatic comic tagline"),
            "characters" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(),
                        "name" to Schema.string(description = "Character name representing concept or actor"),
                        "role" to Schema.string(description = "Protagonist, Mentor, Rival, Catalyst"),
                        "avatarType" to Schema.string(description = "HERO, SCIENTIST, DETECTIVE, ROBOT, STUDENT, ANIME_WARRIOR, WIZARD"),
                        "colorHex" to Schema.string(description = "Hex color like #E53935, #1E88E5, #43A047"),
                        "catchphrase" to Schema.string()
                    )
                )
            ),
            "panels" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "panelNumber" to Schema.integer(),
                        "caption" to Schema.string(description = "Narrator voice-over caption box"),
                        "layoutType" to Schema.string(description = "WIDE, SQUARE, TALL, SPLASH"),
                        "sceneDescription" to Schema.string(description = "Action description"),
                        "charactersPresent" to Schema.array(items = Schema.string()),
                        "dialogues" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "speaker" to Schema.string(),
                                    "text" to Schema.string(),
                                    "bubbleType" to Schema.string(description = "SPEECH, SHOUT, THOUGHT, WHISPER"),
                                    "position" to Schema.string()
                                )
                            )
                        ),
                        "soundEffectText" to Schema.string(description = "POW!, KABOOM!, ZAP!, WHOOSH!"),
                        "soundEffectStyle" to Schema.string(description = "EXPLOSION, IMPACT, SPEED, SPARK"),
                        "visualMood" to Schema.string(description = "DRAMATIC_BURST, SUNNY_ACTION, DARK_SHADOWS, NEON_GLOW, COSMIC_SPACE"),
                        "accentColor" to Schema.string()
                    )
                )
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = comicResponseSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun generateComicFromNote(noteText: String, genre: ComicGenre, panelCount: Int = 5): ComicStrip {
        val prompt = """
            Transform the following notes into an exciting, educational comic strip!
            NOTES:
            \"\"\"
            $noteText
            \"\"\"
            GENRE: ${genre.displayName} (${genre.name})
            PANEL COUNT: $panelCount panels
            
            GUIDELINES:
            1. Personify the core concepts into vivid characters with distinct names, roles, and catchphrases.
            2. Tell a sequential $panelCount-panel narrative arc (Setup, Inciting Beat, Conflict/Climax, Resolution).
            3. Include punchy character dialogue balloons, thoughts, and narrator captions.
            4. Include dynamic comic sound effects (e.g. POW!, KABOOM!, ZAP!, EUREKA!).
            5. Return strict JSON adhering to schema.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        val jsonText = response.text ?: throw IllegalStateException("Model returned empty text.")
        return parseComicJson(jsonText, noteText, genre)
    }

    private fun parseComicJson(jsonString: String, originalNote: String, targetGenre: ComicGenre): ComicStrip {
        val obj = JSONObject(jsonString)
        val title = obj.optString("title", "The Note Chronicles")
        val issueNumber = obj.optString("issueNumber", "Issue #1")
        val summary = obj.optString("summary", "A comic adventure adapted from your notes.")
        val tagline = obj.optString("tagline", "Concepts come to life in full color!")

        val characters = mutableListOf<ComicCharacter>()
        val charArray = obj.optJSONArray("characters")
        if (charArray != null) {
            for (i in 0 until charArray.length()) {
                val c = charArray.getJSONObject(i)
                characters.add(
                    ComicCharacter(
                        id = c.optString("id", "char_$i"),
                        name = c.optString("name", "Hero $i"),
                        role = c.optString("role", "Protagonist"),
                        avatarType = c.optString("avatarType", "HERO"),
                        colorHex = c.optString("colorHex", "#E53935"),
                        catchphrase = c.optString("catchphrase", "")
                    )
                )
            }
        }

        val panels = mutableListOf<ComicPanel>()
        val panelArray = obj.optJSONArray("panels")
        if (panelArray != null) {
            for (i in 0 until panelArray.length()) {
                val p = panelArray.getJSONObject(i)
                val charsPresent = mutableListOf<String>()
                val cpArray = p.optJSONArray("charactersPresent")
                if (cpArray != null) {
                    for (k in 0 until cpArray.length()) { charsPresent.add(cpArray.getString(k)) }
                }
                val dialogues = mutableListOf<SpeechBubble>()
                val dArray = p.optJSONArray("dialogues")
                if (dArray != null) {
                    for (k in 0 until dArray.length()) {
                        val d = dArray.getJSONObject(k)
                        dialogues.add(
                            SpeechBubble(
                                speaker = d.optString("speaker", "Narrator"),
                                text = d.optString("text", "..."),
                                bubbleType = d.optString("bubbleType", "SPEECH"),
                                position = d.optString("position", "TOP_LEFT")
                            )
                        )
                    }
                }
                val sfxText = p.optString("soundEffectText", "")
                val soundEffect = if (sfxText.isNotBlank()) {
                    SoundEffect(sound = sfxText, style = p.optString("soundEffectStyle", "EXPLOSION"), colorHex = p.optString("accentColor", "#FFC107"))
                } else null

                panels.add(
                    ComicPanel(
                        panelNumber = p.optInt("panelNumber", i + 1),
                        caption = p.optString("caption", ""),
                        layoutType = p.optString("layoutType", "SQUARE"),
                        sceneDescription = p.optString("sceneDescription", ""),
                        charactersPresent = charsPresent,
                        dialogues = dialogues,
                        soundEffect = soundEffect,
                        visualMood = p.optString("visualMood", "DRAMATIC_BURST"),
                        accentColor = p.optString("accentColor", "#FFC107")
                    )
                )
            }
        }

        return ComicStrip(
            title = title,
            issueNumber = issueNumber,
            genre = targetGenre,
            originalNote = originalNote,
            summary = summary,
            tagline = tagline,
            characters = characters,
            panels = panels,
            createdAt = System.currentTimeMillis()
        )
    }
}

// ==========================================
// 4. TTS AUDIO NARRATOR HELPER
// ==========================================
class ComicSpeaker(context: Context, private val onSpeakingStateChanged: (Boolean) -> Unit = {}) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { onSpeakingStateChanged(true) }
                    override fun onDone(id: String?) { onSpeakingStateChanged(false) }
                    override fun onError(id: String?) { onSpeakingStateChanged(false) }
                })
            }
        }
    }

    fun speakPanel(panel: ComicPanel) {
        if (!isInitialized) return
        stop()
        val textToSpeak = buildString {
            if (panel.caption.isNotBlank()) append("Narrator: ${panel.caption}. ")
            if (panel.soundEffect != null && panel.soundEffect.sound.isNotBlank()) append("${panel.soundEffect.sound}! ")
            panel.dialogues.forEach { d ->
                if (d.speaker.isNotBlank()) append("${d.speaker} says: ")
                append("${d.text}. ")
            }
        }
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "panel_${panel.panelNumber}")
    }

    fun stop() {
        tts?.stop()
        onSpeakingStateChanged(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

// ==========================================
// 5. VIEWMODEL & STATE MANAGEMENT
// ==========================================
enum class Screen { CREATE, DETAIL, LIBRARY }

class ComicViewModel(
    private val dao: ComicDao,
    private val aiService: ComicGeneratorService
) : ViewModel() {
    val savedComics: StateFlow<List<ComicStrip>> = dao.getAllComics().map { list -> list.map { it.toDomain() } }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentScreen = MutableStateFlow(Screen.CREATE)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedComic = MutableStateFlow<ComicStrip?>(null)
    val selectedComic: StateFlow<ComicStrip?> = _selectedComic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun navigateTo(s: Screen) { _currentScreen.value = s }
    fun selectComic(c: ComicStrip) { _selectedComic.value = c; _currentScreen.value = Screen.DETAIL }

    fun generateComic(noteText: String, genre: ComicGenre, panelCount: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val newComic = aiService.generateComicFromNote(noteText, genre, panelCount)
                val id = dao.insertComic(ComicEntity.fromDomain(newComic))
                _selectedComic.value = newComic.copy(id = id)
                _currentScreen.value = Screen.DETAIL
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ComicViewModel", "Generation error", e)
                _errorMessage.value = e.localizedMessage ?: "Generation failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateComic(comic: ComicStrip) {
        viewModelScope.launch {
            dao.updateComic(ComicEntity.fromDomain(comic))
            _selectedComic.value = comic
        }
    }

    fun deleteComic(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            if (_selectedComic.value?.id == id) {
                _selectedComic.value = null
                _currentScreen.value = Screen.LIBRARY
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return ComicViewModel(db.comicDao(), ComicGeneratorService()) as T
        }
    }
}

// ==========================================
// 6. MAIN ACTIVITY & ENTRY POINT
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            com.example.ui.theme.MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ComicAppNavigation()
                }
            }
        }
    }
}

@Composable
fun ComicAppNavigation(
    viewModel: ComicViewModel = viewModel(factory = ComicViewModel.Factory(LocalContext.current.applicationContext))
) {
    val screen by viewModel.currentScreen.collectAsState()
    val comics by viewModel.savedComics.collectAsState()
    val selected by viewModel.selectedComic.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    when (screen) {
        Screen.CREATE -> com.example.ui.screens.NoteInputScreen(
            onGenerateComic = { note, genre, panels -> viewModel.generateComic(note, genre, panels) },
            onNavigateToLibrary = { viewModel.navigateTo(Screen.LIBRARY) },
            isLoading = loading,
            errorMessage = error
        )
        Screen.DETAIL -> selected?.let { comic ->
            com.example.ui.screens.ComicDetailScreen(
                comic = comic,
                onBack = { viewModel.navigateTo(Screen.CREATE) },
                onUpdateComic = { viewModel.updateComic(it) }
            )
        } ?: viewModel.navigateTo(Screen.CREATE)
        Screen.LIBRARY -> com.example.ui.screens.ComicsLibraryScreen(
            comics = comics,
            onSelectComic = { viewModel.selectComic(it) },
            onDeleteComic = { viewModel.deleteComic(it) },
            onNavigateToCreate = { viewModel.navigateTo(Screen.CREATE) },
            onBack = { viewModel.navigateTo(Screen.CREATE) }
        )
    }
}