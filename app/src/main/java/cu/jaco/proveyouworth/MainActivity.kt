package cu.jaco.proveyouworth

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiViewModel = ViewModelProvider(
            this
        ).get(ApiViewModel::class.java)

        apiViewModel.main(this)

    }


}
