package com.example.gpslessclient

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gpslessclient.api.TokenManager
import com.example.gpslessclient.databinding.ActivityLoginBinding
import com.example.gpslessclient.model.http.request.AuthRequest
import com.example.gpslessclient.api.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        // Если уже есть токен, сразу идем в MainActivity
        val token = tokenManager.getAccessToken()
        if (!token.isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            if (username.isNotBlank() && password.isNotBlank()) {
                login(username, password)
            } else {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
    }

    private fun login(username: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.authApi.login(AuthRequest(username, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val accessToken = body.accessToken
                        val refreshToken = body.refreshToken

                        // Проверяем, что токены не null
                        if (!accessToken.isNullOrEmpty() && !refreshToken.isNullOrEmpty()) {
                            tokenManager.saveTokens(accessToken, refreshToken)
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            showError("Получены пустые токены")
                        }
                    } else {
                        showError("Пустой ответ от сервера")
                    }
                } else {
                    when (response.code()) {
                        401 -> showError("Неверный логин или пароль")
                        else -> showError("Ошибка: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                showError("Ошибка сети: ${e.message}")
            } catch (e: HttpException) {
                showError("Ошибка сервера: ${e.code()}")
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}