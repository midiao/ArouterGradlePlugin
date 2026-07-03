package cn.jailedbird.arouter.ksp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter

@Route(path = "/app/ThirdActivity")
class ThirdActivity : AppCompatActivity() {

    @Autowired
    @JvmField
    var source: String? = null

    @Autowired
    @JvmField
    var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ARouter.getInstance().inject(this)
        setContentView(R.layout.third_main)

        findViewById<TextView>(R.id.thirdRouteResult).text = buildString {
            appendLine("Third Activity")
            appendLine("source=${source ?: "unknown"}")
            append("message=${message ?: "empty"}")
        }
    }
}
