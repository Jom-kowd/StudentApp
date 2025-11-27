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
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
val Gold = Color(0xFFFFD700)
val WarningRed = Color(0xFFD32F2F) // For deadlines < 24h

// PH Grading Colors
val GradeA = Color(0xFF4CAF50) // 1.0 - 1.25
val GradeB = Color(0xFF8BC34A) // 1.5 - 1.75
val GradeC = Color(0xFFFFC107) // 2.0 - 2.5
val GradeD = Color(0xFFFF9800) // 2.75 - 3.0
val GradeF = Color(0xFFF44336) // 5.0 (Fail)

// PH Context Category Colors
val CatActivity = Color(0xFF42A5F5)   // Blue
val CatQuiz = Color(0xFFAB47BC)       // Purple
val CatExam = Color(0xFFEF5350)       // Red
val CatThesis = Color(0xFFFFA726)     // Orange
val CatOrg = Color(0xFF66BB6A)        // Green

// --- 2. MODEL (DATA CLASSES) ---
data class Assignment(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val deadline: Long, // Stores Date AND Time in millis
    val category: String,
    val isCompleted: Boolean = false
) {
    fun getFormattedDate(): String {
        // Updated format: "Nov 28, 2025 at 11:59 PM"
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(deadline))
    }

    // Check if due within 24 hours (Urgent!)
    fun isUrgent(): Boolean {
        val now = System.currentTimeMillis()
        val diff = deadline - now
        return diff in 0..(24 * 60 * 60 * 1000) && !isCompleted
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
    val avatarId: Int = 0,
    val imageUri: String? = null,
    val category: String = "Computer Science"
)

data class Subject(
    val id: Long = System.currentTimeMillis(),
    val name: String,       // e.g., "Purposive Communication"
    val code: String,       // e.g., "GE 101"
    val units: Int,         // PH colleges use "Units" (usually 3.0)
    val schedule: String,   // e.g., "MWF 10:00 - 11:30 AM"
    val grade: Double? = null,
    val yearLevel: String,  // "1st Year", "2nd Year"
    val semester: String    // "1st Sem", "2nd Sem", "Summer"
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
    var subjects = mutableStateListOf<Subject>()
        private set
    var userProfile by mutableStateOf(UserProfile())
        private set

    // Filtering State
    var selectedYear by mutableStateOf("1st Year")
    var selectedSem by mutableStateOf("1st Sem")

    // Timer State
    var timerDuration by mutableLongStateOf(25 * 60 * 1000L)
        private set
    var timeLeft by mutableLongStateOf(25 * 60 * 1000L)
        private set
    var isTimerRunning by mutableStateOf(false)
        private set
    private var timerJob: Job? = null

    init {
        loadData()
    }

    // --- Profile ---
    fun updateProfile(name: String, school: String, bio: String, avatarId: Int, imageUri: String?, category: String) {
        userProfile = UserProfile(name, school, bio, avatarId, imageUri, category)
        saveData()
    }

    // --- Subjects ---
    fun addSubject(name: String, code: String, units: Int, schedule: String) {
        val newSubject = Subject(name = name, code = code, units = units, schedule = schedule, yearLevel = selectedYear, semester = selectedSem)
        subjects.add(0, newSubject)
        saveData()
    }

    fun updateSubjectGrade(subject: Subject, newGrade: Double) {
        val index = subjects.indexOfFirst { it.id == subject.id }
        if (index != -1) {
            subjects[index] = subjects[index].copy(grade = newGrade)
            saveData()
        }
    }

    fun deleteSubject(subject: Subject) {
        subjects.remove(subject)
        saveData()
    }

    fun calculateSemGWA(): Double {
        val filtered = subjects.filter { it.yearLevel == selectedYear && it.semester == selectedSem && it.grade != null }
        if (filtered.isEmpty()) return 0.0
        val totalUnits = filtered.sumOf { it.units }
        val totalPoints = filtered.sumOf { (it.grade ?: 0.0) * it.units }
        return if (totalUnits > 0) totalPoints / totalUnits else 0.0
    }

    // --- Tasks (Assignments) ---
    fun addAssignment(title: String, deadline: Long, category: String) {
        val newItem = Assignment(title = title, deadline = deadline, category = category)
        assignments.add(0, newItem)
        assignments.sortBy { it.deadline } // Sort by deadline automatically
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

    // --- Notes ---
    fun addNote(content: String) {
        notes.add(0, BrainNote(content = content, colorIndex = (0..3).random()))
        saveData()
    }

    fun deleteNote(note: BrainNote) {
        notes.remove(note)
        saveData()
    }

    // --- Timer ---
    fun toggleTimer() {
        if (isTimerRunning) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft -= 1000L
            }
            isTimerRunning = false
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
            getApplication(), assignment.id.toInt(), intent,
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
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("assignments", gson.toJson(assignments))
        editor.putString("notes", gson.toJson(notes))
        editor.putString("subjects", gson.toJson(subjects))
        editor.putString("profile", gson.toJson(userProfile))
        editor.apply()
    }

    private fun loadData() {
        val assignmentJson = prefs.getString("assignments", null)
        val noteJson = prefs.getString("notes", null)
        val subjectJson = prefs.getString("subjects", null)
        val profileJson = prefs.getString("profile", null)

        if (assignmentJson != null) assignments.addAll(gson.fromJson(assignmentJson, object : TypeToken<List<Assignment>>() {}.type))
        if (noteJson != null) notes.addAll(gson.fromJson(noteJson, object : TypeToken<List<BrainNote>>() {}.type))
        if (subjectJson != null) subjects.addAll(gson.fromJson(subjectJson, object : TypeToken<List<Subject>>() {}.type))
        if (profileJson != null) userProfile = gson.fromJson(profileJson, UserProfile::class.java)
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
    if (showSplash) SplashScreen() else StudentApp(viewModel)
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepViolet, SoftViolet))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.School, "Logo", tint = Color.White, modifier = Modifier.size(100.dp))
            Spacer(Modifier.height(16.dp))
            Text("Student Manager", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = DeepViolet,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Assignment, "Tasks") }, label = { Text("Tasks") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Lightbulb, "Notes") }, label = { Text("Notes") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Timer, "Focus") }, label = { Text("Focus") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Book, "Subjects") }, label = { Text("Subjects") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(OffWhite).padding(padding)) {
            if (selectedTab != 2) TopHeader(viewModel.userProfile) { showProfileDialog = true }

            when (selectedTab) {
                0 -> {
                    DashboardSection(viewModel.getProgress())
                    AssignmentList(viewModel.assignments, { viewModel.toggleAssignment(it) }, { viewModel.deleteAssignment(it) })
                }
                1 -> NoteGrid(viewModel.notes) { viewModel.deleteNote(it) }
                2 -> FocusScreen(viewModel)
                3 -> SubjectsScreen(viewModel)
            }
        }

        if (showAddDialog) {
            when (selectedTab) {
                0 -> AddAssignmentDialog({ showAddDialog = false }) { t, d, c -> viewModel.addAssignment(t, d, c); showAddDialog = false }
                1 -> AddNoteDialog({ showAddDialog = false }) { c -> viewModel.addNote(c); showAddDialog = false }
                3 -> AddSubjectDialog({ showAddDialog = false }) { name, code, units, sched ->
                    viewModel.addSubject(name, code, units, sched)
                    showAddDialog = false
                }
            }
        }

        if (showProfileDialog) {
            ProfileDialog(
                profile = viewModel.userProfile,
                onDismiss = { showProfileDialog = false },
                onConfirm = { name, school, bio, avatarId, imgUri, cat ->
                    viewModel.updateProfile(name, school, bio, avatarId, imgUri, cat)
                    showProfileDialog = false
                }
            )
        }
    }
}

// --- UI SECTIONS ---

@Composable
fun TopHeader(profile: UserProfile, onProfileClick: () -> Unit) {
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star, Icons.Default.AccountCircle, Icons.Default.Pets)
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hello, ${profile.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
            Text("${profile.category} • ${profile.school}", style = MaterialTheme.typography.bodySmall, color = TextGray)
            Spacer(modifier = Modifier.height(4.dp))
            Text("\"${profile.bio}\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = DeepViolet)
        }
        Surface(shape = CircleShape, color = SoftViolet.copy(alpha = 0.2f), modifier = Modifier.size(70.dp).clickable { onProfileClick() }) {
            Box(contentAlignment = Alignment.Center) {
                if (profile.imageUri != null) {
                    coil.compose.AsyncImage(
                        model = profile.imageUri,
                        contentDescription = "Profile Photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val safeAvatarId = profile.avatarId.coerceIn(0, avatars.lastIndex)
                    Icon(avatars[safeAvatarId], "Profile", tint = DeepViolet, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardSection(progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 1000), label = "progress")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = DeepViolet), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your Progress", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", color = SoftTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = ElectricTeal, trackColor = SoftViolet)
        }
    }
}

@Composable
fun AssignmentList(assignments: List<Assignment>, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
        items(assignments, key = { it.id }) { item -> AssignmentCard(item, onToggle, onDelete) }
    }
}

@Composable
fun AssignmentCard(item: Assignment, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    val cardColor = if (item.isCompleted) OffWhite else CardWhite
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    // Updated PH Categories
    val badgeColor = when (item.category) {
        "Activity" -> CatActivity
        "Quiz" -> CatQuiz
        "Major Exam" -> CatExam
        "Thesis/Research" -> CatThesis
        else -> CatOrg
    }

    // Urgent logic: Red border if < 24 hours and not done
    val borderStroke = if (item.isUrgent()) androidx.compose.foundation.BorderStroke(1.dp, WarningRed) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(if (item.isCompleted) 0.dp else 4.dp),
        border = borderStroke
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconToggleButton(checked = item.isCompleted, onCheckedChange = { onToggle(item) }, colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = OffWhite, contentColor = Color.Gray, checkedContainerColor = ElectricTeal, checkedContentColor = Color.White)) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done") else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = textColor, textDecoration = textDeco)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.category, color = badgeColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp, 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))

                    // Shows Time now! e.g. "Today at 11:59 PM"
                    val dateText = if(item.isUrgent()) "DUE SOON: ${item.getFormattedDate()}" else item.getFormattedDate()
                    Text(dateText, style = MaterialTheme.typography.labelMedium, color = if(item.isUrgent()) WarningRed else TextGray)
                }
            }
            IconButton({ onDelete(item) }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray) }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Text("Brain Dump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(notes) { note ->
            val noteColors = listOf(Color(0xFFFFF9C4), Color(0xFFE1BEE7), Color(0xFFB2DFDB), Color(0xFFFFCCBC))
            Card(colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]), modifier = Modifier.height(150.dp)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(note.content, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                    IconButton({ onDelete(note) }, modifier = Modifier.align(Alignment.End).size(24.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(0.5f)) }
                }
            }
        }
    }
}

@Composable
fun FocusScreen(viewModel: StudentViewModel) {
    val progress = if (viewModel.timerDuration > 0) viewModel.timeLeft.toFloat() / viewModel.timerDuration.toFloat() else 0f
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Focus Mode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DeepViolet)
        Text("Stay productive.", style = MaterialTheme.typography.bodyMedium, color = TextGray)
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = ElectricTeal, trackColor = SoftViolet.copy(0.2f), strokeWidth = 12.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(viewModel.timeLeft), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = TextDark)
                Text(if (viewModel.isTimerRunning) "Focusing..." else "Paused", color = if(viewModel.isTimerRunning) ElectricTeal else Color.Gray, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ viewModel.resetTimer() }, modifier = Modifier.size(56.dp).background(OffWhite, CircleShape).border(1.dp, Color.LightGray, CircleShape)) { Icon(Icons.Outlined.Refresh, "Reset", tint = TextDark) }
            IconButton({ viewModel.toggleTimer() }, modifier = Modifier.size(80.dp).background(DeepViolet, CircleShape).shadow(8.dp, CircleShape)) {
                Icon(if (viewModel.isTimerRunning) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle, "Toggle", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 25, 45).forEach { mins ->
                FilterChip(selected = viewModel.timerDuration == mins * 60 * 1000L, onClick = { viewModel.setDuration(mins) }, label = { Text("$mins min") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftViolet, selectedLabelColor = Color.White))
            }
        }
    }
}

@Composable
fun SubjectsScreen(viewModel: StudentViewModel) {
    val currentSubjects = viewModel.subjects.filter {
        it.yearLevel == viewModel.selectedYear && it.semester == viewModel.selectedSem
    }

    val gwa = viewModel.calculateSemGWA()
    var showGradeDialog by remember { mutableStateOf<Subject?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Academic Year", style = MaterialTheme.typography.labelMedium, color = TextGray)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            YearSemDropdown(value = viewModel.selectedYear, options = listOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year"), onValueChange = { viewModel.selectedYear = it })
            YearSemDropdown(value = viewModel.selectedSem, options = listOf("1st Sem", "2nd Sem", "Summer"), onValueChange = { viewModel.selectedSem = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DeepViolet), elevation = CardDefaults.cardElevation(8.dp)) {
            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Semester GWA", color = Color.White.copy(0.8f))
                    Text(if (gwa > 0) "%.2f".format(gwa) else "No Grades", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Gold)
                }
                Icon(Icons.Outlined.Grade, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("${viewModel.selectedYear} - ${viewModel.selectedSem} Subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (currentSubjects.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Text("No subjects added.", color = TextGray)
                    Text("Tap + to enroll subjects.", color = TextGray)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(currentSubjects, key = { it.id }) { subject ->
                    SubjectItem(subject, onClick = { showGradeDialog = subject }, onDelete = { viewModel.deleteSubject(subject) })
                }
            }
        }
    }

    if (showGradeDialog != null) {
        AddGradeDialog(subject = showGradeDialog!!, onDismiss = { showGradeDialog = null }, onConfirm = { grade -> viewModel.updateSubjectGrade(showGradeDialog!!, grade); showGradeDialog = null })
    }
}

@Composable
fun SubjectItem(subject: Subject, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = OffWhite, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(50.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(subject.units.toString(), fontWeight = FontWeight.Bold, color = DeepViolet)
                    Text("Units", style = MaterialTheme.typography.labelSmall, color = TextGray, fontSize = 9.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.code, style = MaterialTheme.typography.labelSmall, color = ElectricTeal, fontWeight = FontWeight.Bold)
                Text(subject.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subject.schedule, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            if (subject.grade != null) {
                val g = subject.grade
                val color = if (g <= 1.25) GradeA else if (g <= 1.75) GradeB else if (g <= 2.5) GradeC else if (g <= 3.0) GradeD else GradeF
                Text("%.1f".format(g), fontWeight = FontWeight.Bold, color = color)
            } else {
                Text("--", color = Color.LightGray)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Delete, "Del", tint = Color.LightGray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSemDropdown(value: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true, onClick = { expanded = true }, label = { Text(value) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CardWhite, selectedLabelColor = DeepViolet),
            border = FilterChipDefaults.filterChipBorder(borderColor = Color.LightGray, selectedBorderColor = DeepViolet, enabled = true, selected = true)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false }) }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

// --- DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssignmentDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    // PH Context: Changed "Homework" to "Activity", etc.
    var selectedCategory by remember { mutableStateOf("Activity") }
    val categories = listOf("Activity", "Quiz", "Major Exam", "Thesis/Research", "Org Work")

    // Date & Time Logic
    val calendar = Calendar.getInstance()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = false
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Formatting for display
    val selectedDate = Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Format Time for Display
    val timeText = String.format("%02d:%02d %s",
        if(timePickerState.hour % 12 == 0) 12 else timePickerState.hour % 12,
        timePickerState.minute,
        if(timePickerState.hour >= 12) "PM" else "AM"
    )

    // Dialogs for Date & Time
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ showDatePicker = false }) { Text("OK") } }) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } },
            text = { TimeInput(state = timePickerState) } // Use TimeInput for keyboard entry or TimePicker for clock dial
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (e.g., Chapter 1 Draft)") }, modifier = Modifier.fillMaxWidth())

                // DATE Selector
                OutlinedTextField(
                    value = dateFormat.format(selectedDate),
                    onValueChange = {}, label = { Text("Date") }, readOnly = true,
                    trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Default.DateRange, "Pick Date") } },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )

                // TIME Selector
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {}, label = { Text("Time") }, readOnly = true,
                    trailingIcon = { IconButton({ showTimePicker = true }) { Icon(Icons.Default.AccessTime, "Pick Time") } },
                    modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true }
                )

                // Category Chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    categories.take(3).forEach { cat ->
                        FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat, fontSize = 10.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button({
                if (title.isNotEmpty()) {
                    // Combine Date + Time
                    val c = Calendar.getInstance()
                    c.timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    onConfirm(title, c.timeInMillis, selectedCategory)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Add Task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Quick Thought") },
        text = { OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3) },
        confirmButton = { Button({ if (content.isNotEmpty()) onConfirm(content) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubjectDialog(onDismiss: () -> Unit, onConfirm: (String, String, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("3") }
    var schedule by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Subject Code (e.g., IT 101)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = units, onValueChange = { if(it.all { c -> c.isDigit() }) units = it }, label = { Text("Units") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = schedule, onValueChange = { schedule = it }, label = { Text("Schedule (e.g., MWF 9-10AM)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotEmpty() && code.isNotEmpty()) onConfirm(name, code, units.toIntOrNull() ?: 3, schedule) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Enroll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddGradeDialog(subject: Subject, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var gradeInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Final Grade") },
        text = {
            Column {
                Text("For ${subject.code}: ${subject.name}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = gradeInput, onValueChange = { gradeInput = it }, label = { Text("Grade (e.g., 1.50 or 95)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { val g = gradeInput.toDoubleOrNull(); if (g != null) onConfirm(g) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save Grade") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, String?, String) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var school by remember { mutableStateOf(profile.school) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarId by remember { mutableIntStateOf(profile.avatarId) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(if (profile.imageUri != null) android.net.Uri.parse(profile.imageUri) else null) }
    val categories = listOf("Computer Science", "Business & Eco", "Arts & Design", "Engineering", "Medicine", "Law & Politics")
    var selectedCategory by remember { mutableStateOf(profile.category) }
    var expandedCategory by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: android.net.Uri? -> if (uri != null) imageUri = uri }
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star, Icons.Default.AccountCircle, Icons.Default.Pets)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DeepViolet) },
        text = {
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Surface(shape = CircleShape, color = OffWhite, border = androidx.compose.foundation.BorderStroke(2.dp, ElectricTeal), modifier = Modifier.size(100.dp)) {
                            if (imageUri != null) {
                                coil.compose.AsyncImage(model = imageUri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) { Icon(avatars[avatarId.coerceIn(0, avatars.lastIndex)], null, tint = DeepViolet, modifier = Modifier.size(50.dp)) }
                            }
                        }
                        SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }, containerColor = Gold, contentColor = TextDark) { Icon(Icons.Default.Edit, "Pick Photo") }
                    }
                }
                item {
                    Text("Or pick an avatar:", style = MaterialTheme.typography.labelSmall, color = TextGray)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        avatars.forEachIndexed { index, icon ->
                            val isSelected = (avatarId == index && imageUri == null)
                            IconButton(onClick = { avatarId = index; imageUri = null }) { Icon(icon, contentDescription = null, tint = if (isSelected) DeepViolet else Color.LightGray, modifier = Modifier.size(if (isSelected) 32.dp else 24.dp)) }
                        }
                    }
                }
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    ExposedDropdownMenuBox(expanded = expandedCategory, onExpandedChange = { expandedCategory = !expandedCategory }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = selectedCategory, onValueChange = {}, readOnly = true, label = { Text("Major / Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                            categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { selectedCategory = category; expandedCategory = false }) }
                        }
                    }
                }
                item { OutlinedTextField(value = school, onValueChange = { school = it }, label = { Text("School / University") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio / Status") }, modifier = Modifier.fillMaxWidth(), maxLines = 3) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, school, bio, avatarId, imageUri?.toString(), selectedCategory) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save Profile") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}