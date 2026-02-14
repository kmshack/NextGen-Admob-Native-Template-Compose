package com.soosu.nextgen.admobnative.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.soosu.nextgen.admobnative.SplashAdLoader
import com.soosu.nextgen.admobnative.SplashAdResult
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SplashScreen()
        }

        val app = application as SampleApplication

        lifecycleScope.launch {
            // 1. 스플래시 광고 전체 플로우 실행 (동의 → 초기화 → 로드 → 표시)
            val result = SplashAdLoader.execute(this@SplashActivity, app.admobConfig)

            when (result) {
                SplashAdResult.AD_SHOWN -> { /* 광고 표시 완료 */ }
                SplashAdResult.AD_NOT_AVAILABLE -> { /* 광고 없음 또는 로드 실패 */ }
                SplashAdResult.SKIPPED -> { /* 설정에 의해 스킵됨 */ }
            }

            // 2. 다음 포그라운드 전환 시 광고 스킵 (스플래시 → 메인 전환이므로)
            app.adLifecycleObserver.ignoreNextForegroundAd()

            // 3. 포그라운드 광고 프리로드
            app.appOpenAdManager.loadAd(this@SplashActivity)

            // 4. 메인 화면으로 이동
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AdMob Sample",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
        )
    }
}
