package com.siryul.talkbar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

object AppInfo {
  /** 개발(debug) 빌드인가. 셸 테스트 옵션 같은 개발용 기능은 여기서만 보인다. */
  fun isDebuggable(ctx: Context) = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

  /** 삼성 폰인가. Now Bar 개발자 옵션·'실시간 정보' 안내는 삼성에서만 보인다. */
  val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
