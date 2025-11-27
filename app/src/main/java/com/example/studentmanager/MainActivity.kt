package com.example.studentmanager

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- 1. THEME & COLORS ---
val DeepViolet = Color(0xFF4A148C)
val SoftViolet = Color(0xFF7C43BD)
val ElectricTeal = Color(0xFF009688)
val SoftTeal = Color(0xFF80CBC4)
val OffWhite = Color(0xFFF5F5F7)
val CardWhite = Color(0xFFFFFFFF)
val TextDark = Color(0xFF1A1A1A)
val TextGray = Color(0xFF757575)
val Gold = Color(0xFFFFD700) // For developer credits

// Category Colors
val CatHomework = Color(0xFFE57373)
val CatExam = Color(0xFFBA68C8)
val CatProject = Color(0xFF64B5F6)
val CatPersonal = Color(0xFFFFB74D)

// --- 2. MODEL (DATA CLASSES) ---
data class Assignment(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val deadline: Long,
    val category: String,
    val isCompleted: Boolean = false
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(deadline))
    }
}

data class BrainNote(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val colorIndex: Int = 0
)

data class UserProfile(
    val name: String = "Student",
    val school: String = "Android Academy",
    val bio: String = "Coding my future.",
    val avatarId: Int = 0 // 0=Face, 1=Cool, 2=Nerd, 3=Star
)

// --- 3. NOTIFICATION LOGIC ---
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TITLE") ?: "Task Due!"
        val message = intent.getStringExtra("MESSAGE") ?: "You have a deadline coming up."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "student_tasks_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Task Deadlines", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// --- 4. VIEWMODEL (LOGIC) ---
class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("student_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var assignments = mutableStateListOf<Assignment>()
        private set
    var notes = mutableStateListOf<BrainNote>()
        private set
    var userProfile by mutableStateOf(UserProfile())
        private set

    // --- POMODORO TIMER STATE ---
    var timerDuration by mutableLongStateOf(25 * 60 * 1000L) // Default 25 mins
        private set
    var timeLeft by mutableLongStateOf(25 * 60 * 1000L)
        private set
    var isTimerRunning by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    init {
        loadData()
    }

    // --- Actions ---
    fun updateProfile(name: String, school: String, bio: String, avatarId: Int) {
        userProfile = UserProfile(name, school, bio, avatarId)
        saveData()
    }

    fun addAssignment(title: String, deadline: Long, category: String) {
        val newItem = Assignment(title = title, deadline = deadline, category = category)
        assignments.add(0, newItem)
        saveData()
        scheduleNotification(newItem)
    }

    fun toggleAssignment(assignment: Assignment) {
        val index = assignments.indexOfFirst { it.id == assignment.id }
        if (index != -1) {
            assignments[index] = assignments[index].copy(isCompleted = !assignment.isCompleted)
            saveData()
        }
    }

    fun deleteAssignment(assignment: Assignment) {
        assignments.remove(assignment)
        saveData()
    }

    fun addNote(content: String) {
        notes.add(0, BrainNote(content = content, colorIndex = (0..3).random()))
        saveData()
    }

    fun deleteNote(note: BrainNote) {
        notes.remove(note)
        saveData()
    }

    // --- Timer Logic ---
    fun toggleTimer() {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft -= 1000L
            }
            isTimerRunning = false
            // Optional: You could trigger a notification here when done
        }
    }

    private fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        timeLeft = timerDuration
    }

    fun setDuration(minutes: Int) {
        resetTimer()
        timerDuration = minutes * 60 * 1000L
        timeLeft = timerDuration
    }

    private fun scheduleNotification(assignment: Assignment) {
        val intent = Intent(getApplication(), NotificationReceiver::class.java).apply {
            putExtra("TITLE", "Deadline: ${assignment.title}")
            putExtra("MESSAGE", "Your ${assignment.category} task is due today!")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            assignment.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("assignments", gson.toJson(assignments))
        editor.putString("notes", gson.toJson(notes))
        editor.putString("profile", gson.toJson(userProfile))
        editor.apply()
    }

    private fun loadData() {
        val assignmentJson = prefs.getString("assignments", null)
        val noteJson = prefs.getString("notes", null)
        val profileJson = prefs.getString("profile", null)

        if (assignmentJson != null) {
            val type = object : TypeToken<List<Assignment>>() {}.type
            assignments.addAll(gson.fromJson(assignmentJson, type))
        }
        if (noteJson != null) {
            val type = object : TypeToken<List<BrainNote>>() {}.type
            notes.addAll(gson.fromJson(noteJson, type))
        }
        if (profileJson != null) {
            userProfile = gson.fromJson(profileJson, UserProfile::class.java)
        }
    }

    fun getProgress(): Float {
        if (assignments.isEmpty()) return 0f
        return assignments.count { it.isCompleted }.toFloat() / assignments.size.toFloat()
    }
}

// --- 5. UI (COMPOSABLES) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = DeepViolet,
                    secondary = ElectricTeal,
                    background = OffWhite,
                    surface = CardWhite
                )
            ) {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent(viewModel: StudentViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
    } else {
        StudentApp(viewModel)
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepViolet, SoftViolet))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "Logo",
                tint = Color.White,
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Student Manager",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(color = ElectricTeal)
        }
    }
}

@Composable
fun StudentApp(viewModel: StudentViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            // Only show FAB on Assignments (0) and Notes (1) tabs
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = DeepViolet,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, "Tasks") },
                    label = { Text("Assignments") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Lightbulb, "Brain Dump") },
                    label = { Text("Brain Dump") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Timer, "Focus") },
                    label = { Text("Focus") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OffWhite)
                .padding(padding)
        ) {
            // Only show Header on Tasks and Notes tabs to keep Focus Mode clean
            if (selectedTab != 2) {
                TopHeader(
                    profile = viewModel.userProfile,
                    onProfileClick = { showProfileDialog = true }
                )
            }

            // Content Switcher
            when (selectedTab) {
                0 -> {
                    DashboardSection(progress = viewModel.getProgress())
                    AssignmentList(
                        assignments = viewModel.assignments,
                        onToggle = { viewModel.toggleAssignment(it) },
                        onDelete = { viewModel.deleteAssignment(it) }
                    )
                }
                1 -> {
                    NoteGrid(
                        notes = viewModel.notes,
                        onDelete = { viewModel.deleteNote(it) }
                    )
                }
                2 -> {
                    FocusScreen(viewModel = viewModel)
                }
            }
        }

        // Dialogs
        if (showAddDialog) {
            if (selectedTab == 0) {
                AddAssignmentDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { t, d, c ->
                        viewModel.addAssignment(t, d, c)
                        showAddDialog = false
                    }
                )
            } else {
                AddNoteDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { content ->
                        viewModel.addNote(content)
                        showAddDialog = false
                    }
                )
            }
        }

        if (showProfileDialog) {
            ProfileDialog(
                profile = viewModel.userProfile,
                onDismiss = { showProfileDialog = false },
                onConfirm = { n, s, b, a ->
                    viewModel.updateProfile(n, s, b, a)
                    showProfileDialog = false
                }
            )
        }
    }
}

// --- UPDATED UI COMPONENTS ---

@Composable
fun TopHeader(profile: UserProfile, onProfileClick: () -> Unit) {
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hello, ${profile.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = profile.school.ifEmpty { "Ready to learn?" },
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))

        // Avatar Display
        Surface(
            shape = CircleShape,
            color = SoftViolet.copy(alpha = 0.2f),
            modifier = Modifier
                .size(50.dp)
                .clickable { onProfileClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = avatars[profile.avatarId],
                    contentDescription = "Profile",
                    tint = DeepViolet,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardSection(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DeepViolet),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Your Progress", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", color = SoftTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = ElectricTeal,
                trackColor = SoftViolet
            )
        }
    }
}

@Composable
fun AssignmentList(
    assignments: List<Assignment>,
    onToggle: (Assignment) -> Unit,
    onDelete: (Assignment) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(assignments, key = { it.id }) { item ->
            AssignmentCard(item, onToggle, onDelete)
        }
    }
}

@Composable
fun AssignmentCard(
    item: Assignment,
    onToggle: (Assignment) -> Unit,
    onDelete: (Assignment) -> Unit
) {
    val cardColor = if (item.isCompleted) OffWhite else CardWhite
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    val badgeColor = when (item.category) {
        "Homework" -> CatHomework
        "Exam" -> CatExam
        "Project" -> CatProject
        else -> CatPersonal
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCompleted) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconToggleButton(
                checked = item.isCompleted,
                onCheckedChange = { onToggle(item) },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = OffWhite,
                    contentColor = Color.Gray,
                    checkedContainerColor = ElectricTeal,
                    checkedContentColor = Color.White
                )
            ) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done")
                else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textDecoration = textDeco
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.category,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = item.getFormattedDate(), style = MaterialTheme.typography.labelMedium, color = TextGray)
                }
            }

            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text(
                "Brain Dump",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(notes) { note ->
            val noteColors = listOf(Color(0xFFFFF9C4), Color(0xFFE1BEE7), Color(0xFFB2DFDB), Color(0xFFFFCCBC))
            Card(
                colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]),
                modifier = Modifier.height(150.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { onDelete(note) },
                        modifier = Modifier.align(Alignment.End).size(24.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// --- NEW FEATURE: FOCUS SCREEN ---
@Composable
fun FocusScreen(viewModel: StudentViewModel) {
    val progress = if (viewModel.timerDuration > 0) {
        viewModel.timeLeft.toFloat() / viewModel.timerDuration.toFloat()
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Focus Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DeepViolet
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Stay productive. Take breaks.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = ElectricTeal,
                trackColor = SoftViolet.copy(alpha = 0.2f),
                strokeWidth = 12.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(viewModel.timeLeft),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                if (viewModel.isTimerRunning) {
                    Text("Focusing...", color = ElectricTeal, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Paused", color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            IconButton(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier
                    .size(56.dp)
                    .background(OffWhite, CircleShape)
                    .border(1.dp, Color.LightGray, CircleShape)
            ) {
                Icon(Icons.Outlined.Refresh, "Reset", tint = TextDark)
            }

            // Play/Pause Button
            IconButton(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier
                    .size(80.dp)
                    .background(DeepViolet, CircleShape)
                    .shadow(8.dp, CircleShape)
            ) {
                Icon(
                    imageVector = if (viewModel.isTimerRunning) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                    contentDescription = "Toggle Timer",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Duration Toggles
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 25, 45).forEach { mins ->
                FilterChip(
                    selected = viewModel.timerDuration == mins * 60 * 1000L,
                    onClick = { viewModel.setDuration(mins) },
                    label = { Text("$mins min") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoftViolet,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

// Helper to format milliseconds to MM:SS
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// --- DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssignmentDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Homework") }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }
    val categories = listOf("Homework", "Exam", "Project", "Personal")

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())),
                    onValueChange = { },
                    label = { Text("Deadline") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, "Pick Date") } },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    categories.take(2).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    categories.takeLast(2).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty()) onConfirm(title, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), selectedCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextGray) } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Thought") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("What's on your mind?") },
                minLines = 3,
                maxLines = 5
            )
        },
        confirmButton = {
            Button(
                onClick = { if (content.isNotEmpty()) onConfirm(content) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Save Note") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextGray) } }
    )
}

@Composable
fun ProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onConfirm: (String, String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var school by remember { mutableStateOf(profile.school) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarId by remember { mutableIntStateOf(profile.avatarId) }

    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)

    // Using a full screen-like Dialog for more space
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student ID") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar Picker
                Text("Select Avatar", style = MaterialTheme.typography.labelMedium, color = TextGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    avatars.forEachIndexed { index, icon ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = if (avatarId == index) DeepViolet else OffWhite,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clickable { avatarId = index }
                                    .border(2.dp, if (avatarId == index) Gold else Color.Transparent, CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (avatarId == index) Color.White else Color.Gray,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("School / University") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio / Tagline") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                // Developer Credits Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = TextDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = Gold, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("M", fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Developed by",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                "Mark Jomar S. Calmateo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Building the future 🚀",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = Gold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty()) onConfirm(name, school, bio, avatarId) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Save Profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}