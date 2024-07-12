package com.kornelpatyi.csengetesirend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NoLiveLiterals
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.kornelpatyi.csengetesirend.ui.theme.SZIGCsengetésiRendTheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.*

val inversionColorMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)


class MainActivity : ComponentActivity() {
    private val apiData = MutableLiveData(ArrayList<Lessontime>())
    private val lastRefreshedMinutes = MutableLiveData<Int?>(null)
    private val lastHungarianDate = MutableLiveData(getCurrentHungarianDate())
    private val lastRefreshedZDT = MutableLiveData(ZonedDateTime.now())
    private val currZDT = MutableLiveData(ZonedDateTime.now())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = this

        enableEdgeToEdge()
        setContent {
            SZIGCsengetésiRendTheme {
                val apiDataState = apiData.observeAsState()
                val lastRefreshedMinutesState = lastRefreshedMinutes.observeAsState()
                val lastHungarianDateState = lastHungarianDate.observeAsState()
                val lastRefreshedZDTState = lastRefreshedZDT.observeAsState()
                val currZDTState = currZDT.observeAsState()
                getApiData(context, apiData, lastRefreshedZDT)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Row(
                            modifier = Modifier
                                .safeDrawingPadding()
                                .padding(18.dp, 10.dp, 18.dp, 4.dp)
                                .fillMaxWidth()
                        ) {
                            if (isSystemInDarkTheme()) {
                                Image(
                                    modifier = Modifier
                                        .height(28.dp),
                                    painter = painterResource(id = R.drawable.logo_rounded),
                                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(inversionColorMatrix)),
                                    contentDescription = null
                                )
                            } else {
                                Image(
                                    modifier = Modifier
                                        .height(28.dp),
                                    painter = painterResource(id = R.drawable.logo_rounded),
                                    contentDescription = null
                                )
                            }
                            Spacer (
                                modifier = Modifier
                                    .width(11.dp)
                            )
                            Text(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                text = "SZIG csengetési rend"
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TopBar(apiDataState, currZDTState)

                        TableScreen(apiDataState)

                        val filter: IntentFilter = IntentFilter()
                        filter.addAction(Intent.ACTION_TIME_TICK)
                        filter.addAction("action2")

                        SystemBroadcastReceiver(systemAction = Intent.ACTION_TIME_TICK){ intent ->
                            if( intent?.action == Intent.ACTION_TIME_TICK ){
                                lastRefreshedMinutes.value = lastRefreshedZDT.value!!.until(ZonedDateTime.now(), ChronoUnit.MINUTES).toInt()
                                val currHungarianDate = getCurrentHungarianDate()
                                if (lastHungarianDate.value != currHungarianDate) {
                                    var newLessonTimes = ArrayList<Lessontime>()

                                    for (lt in apiData.value!!) {
                                        newLessonTimes.add(Lessontime(
                                            start = ZonedDateTime.of(currHungarianDate, lt.start.toLocalTime(), ZoneId.of("Europe/Budapest")),
                                            end = ZonedDateTime.of(currHungarianDate, lt.end.toLocalTime(), ZoneId.of("Europe/Budapest")),
                                            name = lt.name
                                        ))
                                    }

                                    apiData.value = newLessonTimes
                                    lastHungarianDate.value = getCurrentHungarianDate()
                                }
                                currZDT.value = ZonedDateTime.now()
                            }
                        }

                        val text: String = if (lastRefreshedMinutesState.value === null) {
                            "Unknown"
                        } else if (lastRefreshedMinutesState.value!! < 1) {
                            "Just now"
                        } else if (lastRefreshedMinutesState.value!! < 60) {
                            "Refreshed in ${lastRefreshedMinutesState.value} minutes"
                        } else if (lastRefreshedMinutesState.value!! < 60 * 24) {
                            "Refreshed in ${lastRefreshedMinutesState.value!! / 60} hours"
                        } else {
                            ""
                        }

                        Text(
                            text = text,
                            modifier = Modifier
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lastRefreshedMinutes.value = lastRefreshedZDT.value!!.until(ZonedDateTime.now(), ChronoUnit.MINUTES).toInt()
        val currHungarianDate = getCurrentHungarianDate()
        if (lastHungarianDate.value != currHungarianDate) {
            var newLessonTimes = ArrayList<Lessontime>()

            for (lt in apiData.value!!) {
                newLessonTimes.add(Lessontime(
                    start = ZonedDateTime.of(currHungarianDate, lt.start.toLocalTime(), ZoneId.of("Europe/Budapest")),
                    end = ZonedDateTime.of(currHungarianDate, lt.end.toLocalTime(), ZoneId.of("Europe/Budapest")),
                    name = lt.name
                ))
            }

            apiData.value = null
            apiData.value = newLessonTimes
            lastHungarianDate.value = getCurrentHungarianDate()
        }

        currZDT.value = ZonedDateTime.now()

        Log.d("MyApp", apiData.value.toString())
    }
}


fun getCurrentHungarianDate(): LocalDate {
    return LocalDate.now(ZoneId.of("Europe/Budapest"))
}


@Composable
fun SystemBroadcastReceiver(
    systemAction: String,
    onSystemEvent: (intent: Intent?) -> Unit
) {
    val context = LocalContext.current

    val currentOnSystemEvent by rememberUpdatedState( onSystemEvent )

    DisposableEffect(context, systemAction){

        val intentFilter = IntentFilter( systemAction )

        val receiver = object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                currentOnSystemEvent( intent )
            }
        }

        context.registerReceiver( receiver, intentFilter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}


@Composable
fun TopBar (data: State<ArrayList<Lessontime>?>, currZDTState: State<ZonedDateTime?>) {
    Log.d("MyApp", "Topbar recompose" + currZDTState.value.toString())
    Column(
        modifier = Modifier
            .padding(18.dp, 4.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(5.dp))
            .clip(shape = RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .fillMaxWidth()
            .padding(16.dp, 12.dp)
    ) {
        var userNotification = ""
        var remainingTime = ""

        if (data.value!!.size > 0) {
            if (data.value!![0].start > currZDTState.value) {
                // checks if no lesson has begun yet
                userNotification = "A tanítási nap még nem kezdődött el"
            } else if (data.value!![data.value!!.size - 1].end < currZDTState.value) {
                // checks whether all lessons ended
                userNotification = "A tanítási nap már végetért"
            } else {
                // within the start and the end of the school day
                for (currentLesson in data.value!!) {
                    if (currentLesson.start <= currZDTState.value && currZDTState.value!! <= currentLesson.end) {
                        // checks whether current time is within the start and end time of the i-th lesson
                        userNotification = currentLesson.name.toString() + ". óra"
                        remainingTime =
                            "Még " + currZDTState.value!!.until(currentLesson.end, ChronoUnit.MINUTES).toString() + " perc van hátra"
                        break
                    }
                }
            }

            if (userNotification == "") {
                // checks whether userNotification has a value
                // can only be true if the current time is in one of the breaks
                data.value!!.zipWithNext().forEach{ (first, second) ->
                    if (first.end <= currZDTState.value!! && currZDTState.value!! <= second.start) {
                        userNotification = first.name.toString() + ". szünet"
                        remainingTime = "Még " + currZDTState.value!!.until(second.start, ChronoUnit.MINUTES).toString() + " perc van hátra"
                    }
                }
            }

            Text(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall,
                text = userNotification
            )
            if (remainingTime != "") {
                Text(
                    text = remainingTime
                )
            }
        }
    }
}


fun convertTime (cld: LocalDate, hungarianFormat: String): ZonedDateTime {
    val timePieces = hungarianFormat.split(":");
    val hungarianTime = LocalTime.of(Integer.valueOf(timePieces[0]), Integer.valueOf(timePieces[1]), Integer.valueOf(timePieces[2]))

    val a = ZonedDateTime.of(cld, hungarianTime, ZoneId.of("Europe/Budapest"))

    // val hungarianTime = ZonedDateTime()
    return a
}


fun getApiData (context: Context, apd: MutableLiveData<ArrayList<Lessontime>>, lzdt: MutableLiveData<ZonedDateTime>) {
    val url = "https://ujbudaiszechenyi.hu/api/lessontimes" //  "https://ujbudaiszechenyi.hu/api/lessontimes"
    val requestQueue = Volley.newRequestQueue(context)

    val jr = StringRequest(Request.Method.GET, url,
        { response ->
            val lessontimes = Json.decodeFromString<Lessontimes_p>(response.toString())
            val lessontimes_proper = ArrayList<Lessontime>()
            val currentDate = LocalDate.now()
            for (lt in lessontimes.lessontimes) {
                lessontimes_proper.add(Lessontime(
                    name = lt.name,
                    start = convertTime(currentDate, lt.start),
                    end = convertTime(currentDate, lt.end),
                ))
            }

            apd.value = lessontimes_proper
            lzdt.value = ZonedDateTime.now()
        },
        { error ->
            Log.d("MyApp", "error")
        }
    )
    requestQueue.add(jr)
}


@Serializable
data class Lessontimes_p (
    @SerialName("lessontimes")
    val lessontimes: Array<Lessontime_p>
)

@Serializable
data class Lessontime_p (
    @SerialName("name")
    val name: Int,
    @SerialName("start")
    val start: String,
    @SerialName("end")
    val end: String
)

data class Lessontime (
    val name: Int,
    val start: ZonedDateTime,  // in Europe/Budapest timezone
    val end: ZonedDateTime  // in Europe/Budapest timezone
)



@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    borders: String = "steb"
) {
    val borderColor = MaterialTheme.colorScheme.outline
    Text(
        text = text,
        fontWeight = fontWeight,
        modifier = Modifier
            .weight(weight)
            .drawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if ("s" in borders) {
                    drawLine( //left line
                        start = Offset(x = 0f, y = 0f),
                        end = Offset(x = 0f, y = canvasHeight),
                        strokeWidth = 3f,
                        color = borderColor
                    )
                }
                if ("t" in borders) {
                    drawLine( //top line
                        start = Offset(x = 0f, y = 0f),
                        end = Offset(x = canvasWidth, y = 0f),
                        strokeWidth = 3f,
                        color = borderColor
                    )
                }
                if ("e" in borders) {
                    drawLine( //right line
                        start = Offset(x = canvasWidth, y = 0f),
                        end = Offset(x = canvasWidth, y = canvasHeight),
                        strokeWidth = 3f,
                        color = borderColor
                    )
                }
                if ("b" in borders) {
                    drawLine( //bottom line
                        start = Offset(x = 0f, y = canvasHeight),
                        end = Offset(x = canvasWidth, y = canvasHeight),
                        strokeWidth = 3f,
                        color = borderColor
                    )
                }
            }
            .padding(8.dp),
        color = color
    )
}


fun formatInSystemTimeZone(zdt: ZonedDateTime): String {
    return zdt.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}


@Composable
fun TableScreen(data: State<ArrayList<Lessontime>?>) {
    // Each cell of a column must have the same weight.
    val column1Weight = .25f // 20%
    val column2Weight = .375f // 40%
    val column3Weight = .375f // 40%
    // The LazyColumn will be our table. Notice the use of the weights below

    Column(
        modifier = Modifier
            .padding(18.dp, 18.dp, 18.dp, 4.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(5.dp))
            .clip(shape = RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 6.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            text = "Csengetési rend"
        )
        Column(
            Modifier
                .fillMaxSize()
        ) {
            // Here is the header
            // Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            Row() {
                TableCell(text = "Sorszám", weight = column1Weight, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600)
                TableCell(text = "Kezdete", weight = column2Weight, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600)
                TableCell(text = "Vége", weight = column3Weight, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600)
            }

            // Here are all the lines of your table.
            for (lt in data.value!!) {
                Row(Modifier.fillMaxWidth()) {
                    TableCell(text = lt.name.toString() + ".", weight = column1Weight, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600)
                    TableCell(text = formatInSystemTimeZone(lt.start), weight = column2Weight, color = MaterialTheme.colorScheme.onSurface)
                    TableCell(text = formatInSystemTimeZone(lt.end), weight = column3Weight, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
