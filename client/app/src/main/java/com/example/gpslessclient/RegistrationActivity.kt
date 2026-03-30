package com.example.gpslessclient

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gpslessclient.databinding.ActivityRegistrationBinding
import com.example.gpslessclient.model.http.request.RegistrationRequest
import com.example.gpslessclient.api.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (username.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            register(username, email, password)
        }
    }

    private fun register(username: String, email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApi.signup(
                    RegistrationRequest(username, email, password)
                )
                if (response.isSuccessful) {
                    Toast.makeText(this@RegistrationActivity, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegistrationActivity, LoginActivity::class.java))
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@RegistrationActivity,
                        "Ошибка: ${errorBody ?: "неизвестная"}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this@RegistrationActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                Toast.makeText(this@RegistrationActivity, "Ошибка сервера: ${e.code()}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@RegistrationActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}