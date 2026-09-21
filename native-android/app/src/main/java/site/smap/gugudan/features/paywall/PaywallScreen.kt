package site.smap.gugudan.features.paywall

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalSync
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import site.smap.gugudan.store.PurchaseResult

// 페이월 (iOS PaywallView 이식) — Play Billing 단일 인앱 상품

private data class Benefit(val icon: ImageVector, val title: String, val desc: String)

@Composable
fun PaywallScreen(onDismiss: () -> Unit) {
    val gg = LocalGG.current
    val premium = LocalPremium.current
    val auth = LocalAuth.current
    val sync = LocalSync.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val benefits = listOf(
        Benefit(Icons.Filled.Map, "3D 어드벤처 전 지역", "8개 지역 탐험 · 주민/보스 구구단 대결"),
        Benefit(Icons.Filled.Bolt, "게임 모드 전부 해제", "60초 챌린지 · 서바이벌 · 빈칸 추리 · OX 퀴즈"),
        Benefit(Icons.Filled.AllInclusive, "한 번 결제, 평생 소장", "추가 결제 없음 · 업데이트 콘텐츠도 전부 포함"),
    )

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    // 구매 완료 시 자동 닫힘
    LaunchedEffect(premium.isPremium) { if (premium.isPremium) onDismiss() }

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 닫기
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PressableCard(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "닫기", tint = gg.textMuted, modifier = Modifier.size(24.dp))
            }
        }

        // 히어로
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFF97316)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Coffee, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("커피 한 잔 값으로,\n아이의 구구단 실력을",
            style = suite(FontWeight.ExtraBold, 24).copy(lineHeight = 32.sp),
            color = gg.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("딱 한 번 ${premium.price} — 모든 기능을 평생 이용해요",
            style = suite(FontWeight.Bold, 14), color = gg.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        // 혜택
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            benefits.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) {
                        Icon(b.icon, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(b.title, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                        Text(b.desc, style = suite(FontWeight.Normal, 12), color = gg.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        // 가격 카드
        Box {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(gg.accent.copy(alpha = 0.08f))
                    .border(2.dp, gg.accent, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(premium.price, style = suite(FontWeight.ExtraBold, 36), color = gg.text)
                    Text(" · 딱 한 번", style = suite(FontWeight.Bold, 14), color = gg.textMuted,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("정기 결제 아니에요 — 한 번 사면 끝!", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-12).dp)
                    .clip(CircleShape).background(Color(0xFFFBBF24))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("☕ 커피 한 잔 값", style = suite(FontWeight.ExtraBold, 11), color = Color(0xFF78350F))
            }
        }
        Spacer(Modifier.height(16.dp))

        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, enabled = !busy, onClick = {
            if (!premium.storeReady) {
                notice = "스토어 연결 준비 중이에요. 잠시 후 다시 시도해주세요."
                return@GGButton
            }
            val activity = context as? Activity ?: return@GGButton
            busy = true; notice = null
            premium.purchase(activity) { result ->
                busy = false
                notice = when (result) {
                    PurchaseResult.OK, PurchaseResult.CANCELLED -> null
                    PurchaseResult.PENDING -> "구매가 대기 중이에요 (승인 필요)."
                    PurchaseResult.ERROR -> "구매에 실패했어요. 잠시 후 다시 시도해주세요."
                }
            }
        }) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
            Text(if (busy) "처리 중…" else "평생 이용권 시작하기")
        }

        if (!premium.storeReady) {
            Spacer(Modifier.height(8.dp))
            Text("스토어 연결 준비 중 — 가격은 결제 화면에서 최종 확인돼요.",
                style = suite(FontWeight.Bold, 11), color = gg.warning, textAlign = TextAlign.Center)
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = suite(FontWeight.Bold, 12), color = gg.accent, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val linkStyle = suite(FontWeight.Bold, 11).copy(textDecoration = TextDecoration.Underline)
            FooterLink("구매 복원", linkStyle) {
                premium.restore { ok ->
                    notice = if (ok) "구매가 복원되었어요! 🎉" else "복원할 구매 내역이 없어요."
                    // 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
                    // 서버 구매 등록과 보호자 권한을 시도한다.
                    if (ok) scope.launch { sync.enableGuardianSync(auth, premium.lastPurchaseToken) }
                }
            }
            FooterLink("이용약관", linkStyle) { open(PremiumConfig.Legal.TERMS) }
            FooterLink("개인정보처리방침", linkStyle) { open(PremiumConfig.Legal.PRIVACY) }
        }
    }
}

@Composable
private fun FooterLink(label: String, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Text(label, style = style, color = gg.textMuted)
    }
}
