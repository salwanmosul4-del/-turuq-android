package iq.turuq.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import iq.turuq.core.TuruqApp
import iq.turuq.core.maps.MapController
import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.MapRegion
import iq.turuq.core.model.Place
import iq.turuq.core.model.ReportType
import iq.turuq.core.util.Fmt
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView

private val Teal = Color(0xFF0E7C86)
private val Ink = Color(0xFF0F2A2E)
private val Red = Color(0xFFCE1126)

internal val LocalDark = compositionLocalOf { false }
@Composable internal fun surf(): Color = if (LocalDark.current) Color(0xFF16393E) else Color.White
@Composable internal fun inkc(): Color = if (LocalDark.current) Color(0xFFE6F1EE) else Color(0xFF0F2A2E)
@Composable internal fun selBg(): Color = if (LocalDark.current) Color(0xFF1F5A66) else Color(0xFFE3F3F2)
@Composable internal fun tileBg(): Color = if (LocalDark.current) Color(0xFF1F4A50) else Color(0xFFF1F5F5)

private enum class Panel { None, Report, Maps, Settings }

@Composable
fun TuruqRoot(app: TuruqApp, onNavStateChanged: (Boolean) -> Unit) {
    val themePref0 by app.settings.theme.collectAsState()
    val sys0 = androidx.compose.foundation.isSystemInDarkTheme()
    val dark0 = when (themePref0) { 1 -> false; 2 -> true; else -> sys0 }
    CompositionLocalProvider(LocalDark provides dark0) { TuruqRootInner(app, onNavStateChanged) }
}

@Composable
private fun TuruqRootInner(app: TuruqApp, onNavStateChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var welcome by remember { mutableStateOf(!ctx.getSharedPreferences("turuq", 0).getBoolean("welcomed", false)) }
    var panel by remember { mutableStateOf(Panel.None) }
    var mapsPrompt by remember { mutableStateOf(!ctx.getSharedPreferences("turuq", 0).getBoolean("maps_prompt_done", false)) }
    var query by remember { mutableStateOf("") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var fromQuery by remember { mutableStateOf("") }
    var fromResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var showFrom by remember { mutableStateOf(false) }
    val origin by app.origin.collectAsState()
    var skipQuery by remember { mutableStateOf(false) }
    var skipFrom by remember { mutableStateOf(false) }
    // اقتراحات فورية أثناء الكتابة، تظهر تحت الحقل مباشرة
    LaunchedEffect(query) {
        if (skipQuery) { skipQuery = false; return@LaunchedEffect }
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        results = runCatching { app.api.search(query, app.here()) }.getOrDefault(emptyList())
    }
    LaunchedEffect(fromQuery) {
        if (skipFrom) { skipFrom = false; return@LaunchedEffect }
        if (fromQuery.trim().length < 2) { fromResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        fromResults = runCatching { app.api.search(fromQuery, app.here()) }.getOrDefault(emptyList())
    }

    val routes by app.routes.collectAsState()
    val selected by app.selected.collectAsState()
    val reports by app.reports.collectAsState()
    val navigating by app.navigating.collectAsState()
    val nav by app.nav.state.collectAsState()
    val loc by app.location.last.collectAsState()
    val dest by app.destination.collectAsState()
    val message by app.message.collectAsState()

    var controller by remember { mutableStateOf<MapController?>(null) }
    val themePref by app.settings.theme.collectAsState()
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themePref) { 1 -> false; 2 -> true; else -> sysDark }
    val tiltOn by app.settings.tilt3d.collectAsState()
    val showCams by app.settings.showCameras.collectAsState()
    val showHaz by app.settings.showHazards.collectAsState()
    val showTraf by app.settings.showTraffic.collectAsState()

    androidx.activity.compose.BackHandler(enabled = panel != Panel.None || showFrom) { panel = Panel.None; showFrom = false }
    var lastDark by remember { mutableStateOf(dark) }
    LaunchedEffect(dark, controller) {
        val c = controller ?: return@LaunchedEffect
        if (dark != lastDark) {
            lastDark = dark
            c.load(app.maps.localFiles(), dark) {
                c.showRoutes(routes, selected); c.showReports(reports.filter { app.settings.allows(it.type) })
                c.showDestination(dest?.let { GeoPoint(it.lat, it.lon) })
            }
        }
    }
    LaunchedEffect(navigating) { onNavStateChanged(navigating) }
    LaunchedEffect(controller, routes, selected) { controller?.showRoutes(routes, selected); routes.getOrNull(selected)?.let { if (!navigating) controller?.fit(it) } }
    LaunchedEffect(controller, reports, showCams, showHaz, showTraf) { controller?.showReports(reports.filter { app.settings.allows(it.type) }) }
    LaunchedEffect(controller, dest) { controller?.showDestination(dest?.let { GeoPoint(it.lat, it.lon) }) }
    LaunchedEffect(controller, loc, nav) {
        val c = controller ?: return@LaunchedEffect
        val l = loc ?: return@LaunchedEffect
        val p = nav?.position ?: GeoPoint(l.latitude, l.longitude)
        val b = nav?.bearing ?: l.bearing.toDouble()
        c.setVehicle(p, b)
        if (nav != null) c.follow(p, b, tilt = if (tiltOn) 50.0 else 0.0) else if (routes.isEmpty()) c.overview(p, 13.0)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            MapView(c).also { mv ->
                mv.onCreate(null); mv.onStart(); mv.onResume()
                mv.getMapAsync { m ->
                    val ctl = MapController(c, m)
                    ctl.load(app.maps.localFiles(), dark) { controller = ctl }
                    ctl.overview(app.here(), 12.0)
                }
            }
        })

        if (!navigating) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, color = surf()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("إلى أين تريد الذهاب؟") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            focus.clearFocus(); keyboard?.hide()
                            scope.launch { results = runCatching { app.api.search(query, app.here()) }.getOrDefault(emptyList()) }
                        }),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    )
                }
                if (results.isNotEmpty()) {
                    Surface(Modifier.padding(top = 8.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp, color = surf()) {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(results) { p ->
                                Column(Modifier.fillMaxWidth().clickable { focus.clearFocus(); keyboard?.hide(); skipQuery = true; app.plan(p); results = emptyList(); query = p.name }.padding(14.dp)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, color = inkc())
                                    if (p.area.isNotBlank()) Text(p.area, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                // تصفّح سريع: اكتب اسم محافظة (اختياري) ثم اضغط الفئة
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("uni" to "🎓 جامعات", "gov" to "🏛 دوائر", "hosp" to "🏥 مستشفيات").forEach { (k, label) ->
                        AssistChip(onClick = {
                            focus.clearFocus(); keyboard?.hide()
                            scope.launch { results = runCatching { app.api.browse(k, app.here(), query) }.getOrDefault(emptyList()) }
                        }, label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = surf()))
                    }
                }
                if (showFrom) {
                    Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, color = surf()) {
                        OutlinedTextField(
                            value = fromQuery, onValueChange = { fromQuery = it },
                            placeholder = { Text("ابدأ من: منطقة، مبنى، دائرة…") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                scope.launch { fromResults = runCatching { app.api.search(fromQuery, app.here()) }.getOrDefault(emptyList()) }
                            }),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        )
                    }
                    if (origin != null) TextButton(onClick = { app.setOrigin(null); fromQuery = ""; showFrom = false }) { Text("العودة إلى موقعي") }
                    fromResults.take(5).forEach { p ->
                        Surface(Modifier.fillMaxWidth().padding(top = 4.dp).clickable { focus.clearFocus(); keyboard?.hide(); skipFrom = true; app.setOrigin(p); fromResults = emptyList(); showFrom = false; fromQuery = p.name },
                            shape = RoundedCornerShape(12.dp), color = surf()) {
                            Text(p.name + if (p.area.isNotBlank()) " · " + p.area else "", Modifier.padding(12.dp), color = inkc())
                        }
                    }
                }
            }
        }

        // شريط الملاحة العلوي
        AnimatedVisibility(navigating && nav != null, Modifier.align(Alignment.TopCenter)) {
            nav?.let { st ->
                Surface(Modifier.statusBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = Ink, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(Fmt.dist(st.distToManeuverM), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(st.step.instruction, color = Color.White, fontSize = 16.sp)
                        st.nextWarning?.let { w ->
                            val t = ReportType.fromKey(w.type)
                            Text("⚠ " + (t?.labelAr ?: w.type) + " بعد " + Fmt.dist(st.warningDistM),
                                color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // الأسفل: بطاقات المسارات أو ملخص الرحلة
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
            message?.let { m ->
                Surface(shape = RoundedCornerShape(14.dp), color = Ink, modifier = Modifier.padding(bottom = 8.dp).clickable { app.message.value = null }) {
                    Text(m, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            if (navigating && nav != null) {
                Surface(shape = RoundedCornerShape(20.dp), color = surf(), shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.minutes(nav!!.remainingS), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Teal)
                            Text(Fmt.dist(nav!!.remainingM), color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(Fmt.num(((loc?.speed ?: 0f) * 3.6f).toInt()), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = inkc())
                            Text("كم/س", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(onClick = { panel = Panel.Report }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("أبلغ") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { app.stopNavigation() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("إنهاء") }
                    }
                }
            } else if (routes.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(22.dp), color = surf(), shadowElevation = 8.dp) {
                    Column(Modifier.padding(14.dp)) {
                        routes.take(3).forEachIndexed { i, r ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (i == selected) selBg() else Color.Transparent)
                                    .clickable { app.selected.value = i }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.minutes(r.durationS) + " · " + Fmt.dist(r.distanceM), fontWeight = FontWeight.Bold, color = inkc())
                                    Text(r.label + if (r.warnings.isNotEmpty()) " · " + Fmt.num(r.warnings.size) + " تنبيه على الطريق" else "", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        TextButton(onClick = { showFrom = !showFrom }) { Text("من: " + (origin?.name ?: "موقعي") + "  ·  تغيير نقطة البداية", fontSize = 12.sp, color = Teal) }
                        Button(onClick = { app.startNavigation() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("ابدأ", fontSize = 18.sp) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FloatingActionButton(onClick = { panel = Panel.Maps }, containerColor = Color.White) { Text("🗺") }
                        FloatingActionButton(onClick = { panel = Panel.Settings }, containerColor = Color.White) { Text("⚙", fontSize = 22.sp) }
                    }
                    FloatingActionButton(onClick = { panel = Panel.Report }, containerColor = Teal, contentColor = Color.White) { Text("⚠", fontSize = 22.sp) }
                }
            }
        }

        if (!welcome && panel == Panel.None && !navigating && false && mapsPrompt) {
            Surface(Modifier.align(Alignment.Center).padding(32.dp), shape = RoundedCornerShape(22.dp), color = surf(), shadowElevation = 12.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("حمّل خريطة محافظتك", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = inkc())
                    Text("تعمل الخريطة بلا إنترنت وتوفّر بياناتك، وتُحدَّث تلقائياً.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = { ctx.getSharedPreferences("turuq", 0).edit().putBoolean("maps_prompt_done", true).apply(); mapsPrompt = false; panel = Panel.Maps },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("اختر الخريطة") }
                    TextButton(onClick = { ctx.getSharedPreferences("turuq", 0).edit().putBoolean("maps_prompt_done", true).apply(); mapsPrompt = false }) { Text("لاحقاً") }
                }
            }
        }
        if (panel == Panel.Report) ReportSheet(onPick = { app.report(it); panel = Panel.None }, onClose = { panel = Panel.None })
        if (panel == Panel.Maps) MapsSheet(app, onClose = {
            panel = Panel.None
            // أعد بناء الستايل ليستعمل الخرائط المحمّلة حديثاً ثم أعد رسم الطبقات
            controller?.load(app.maps.localFiles(), dark) {
                controller?.showRoutes(routes, selected); controller?.showReports(reports)
                controller?.showDestination(dest?.let { GeoPoint(it.lat, it.lon) })
            }
        })
        if (panel == Panel.Settings) SettingsScreen(app.settings, onClose = { panel = Panel.None })
        if (welcome) WelcomeRobot(onDone = { ctx.getSharedPreferences("turuq", 0).edit().putBoolean("welcomed", true).apply(); welcome = false })
    }
}

@Composable
private fun ReportSheet(onPick: (ReportType) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = surf()) {
            Column(Modifier.navigationBarsPadding().padding(18.dp)) {
                Text("ماذا ترى على الطريق؟", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = inkc())
                Spacer(Modifier.height(12.dp))
                ReportType.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            Box(
                                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (t.isCamera) Color(0xFFEDE7FB) else tileBg()).clickable { onPick(t) },
                                contentAlignment = Alignment.Center,
                            ) { Text(t.labelAr, fontWeight = FontWeight.Bold, color = inkc(), fontSize = 13.sp) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapsSheet(app: TuruqApp, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("turuq", 0) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifi_only", true)) }
    var regions by remember { mutableStateOf<List<MapRegion>>(emptyList()) }
    var installed by remember { mutableStateOf(app.maps.installed()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { app.maps.manifest().regions }.onSuccess { regions = it }.onFailure { error = if (iq.turuq.core.BuildConfig.MAPS_BASE.isBlank()) "خرائط بلا إنترنت تحتاج خادم الخرائط (لم يُضبط بعد في هذه النسخة التجريبية). الخريطة الحالية تعمل بالإنترنت." else "تعذّر جلب قائمة الخرائط" } }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp).clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = surf()) {
            Column(Modifier.navigationBarsPadding().padding(18.dp)) {
                Text("خرائط بلا إنترنت", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = inkc())
                Text("تُحدَّث تلقائياً حين يصدر إصدار أحدث، ويستمر التنزيل لو أغلقت التطبيق.", fontSize = 12.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; prefs.edit().putBoolean("wifi_only", it).apply() })
                    Spacer(Modifier.width(8.dp)); Text("التنزيل على الواي فاي فقط")
                }
                error?.let { Text(it, color = Red) }
                LazyColumn {
                    items(regions) { r ->
                        val have = installed[r.id]
                        val p by remember(r.id) { iq.turuq.core.maps.MapDownloadWorker.progress(ctx, r.id) }.collectAsState(initial = null)
                        LaunchedEffect(p) { if (p == null) installed = app.maps.installed() }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.Bold, color = inkc())
                                Text(Fmt.num(r.bytes / 1_000_000) + " ميغابايت" + if (have != null && have < r.version) " · يوجد تحديث" else "", fontSize = 12.sp, color = Color.Gray)
                            }
                            when {
                                p != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(progress = { p ?: 0f }, modifier = Modifier.size(28.dp), color = Teal)
                                    Spacer(Modifier.width(6.dp)); Text(Fmt.num(((p ?: 0f) * 100).toInt()) + "٪", fontSize = 12.sp)
                                }
                                have == null || have < r.version -> Button(onClick = {
                                    iq.turuq.core.maps.MapDownloadWorker.enqueue(ctx, r.id, wifiOnly)
                                }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text(if (have == null) "تنزيل" else "تحديث") }
                                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✓ محدّثة", color = Teal, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { app.maps.remove(r.id); installed = app.maps.installed() }) { Text("حذف", color = Red) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * روبوت الترحيب من التصميم: الجسم صورة ثابتة والذراع المشيرة طبقة منفصلة تتأرجح حول الكتف،
 * وفوقه فقاعة الكلام بالعبارة المطلوبة. الصور مولَّدة من ملف التصميم نفسه.
 */
@Composable
private fun WelcomeRobot(onDone: () -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val angle by t.animateFloat(
        -7f, 7f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "a",
    )
    val bob by t.animateFloat(
        0f, 6f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "b",
    )
    Box(Modifier.fillMaxSize().background(Color(0xF20F2A2E)).clickable(onClick = onDone), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFFBF8F1), shadowElevation = 8.dp) {
                Text(
                    "أهلاً بك في أفضل تطبيق عراقي للطرق",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    color = Color(0xFF0F2A2E), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Box(Modifier.fillMaxWidth(0.78f).aspectRatio(390f / 470f).graphicsLayer { translationY = bob }) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(iq.turuq.app.R.drawable.robot_body), null, Modifier.fillMaxSize())
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(iq.turuq.app.R.drawable.robot_arm), null,
                    Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(208f / 390f, 272f / 470f)
                        rotationZ = angle
                    },
                )
            }
            Text("اضغط للمتابعة", color = Color(0xFFB9D5DC))
        }
    }
}
