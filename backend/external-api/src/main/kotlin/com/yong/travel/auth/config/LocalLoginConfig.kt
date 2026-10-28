package com.yong.travel.auth.config

import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.security.LoginUserDetails
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 네이버 OAuth 를 대신하는 임시 고정 계정 로그인.
 * 비밀번호가 코드에 있으므로 `local`·`dev` 에서만 켠다.
 */
@Configuration
@Profile("local", "dev")
class LocalLoginConfig {

    /** 고정 계정. 초대를 주고받아 볼 수 있게 셋을 둔다. */
    private data class LocalAccount(
        val username: String,
        val password: String,
        val name: String,
        val email: String,
    )

    private val accounts = listOf(
        LocalAccount("user1", "password", "사용자1", "user1@example.com"),
        LocalAccount("user2", "password", "사용자2", "user2@example.com"),
        LocalAccount("user3", "password", "사용자3", "user3@example.com"),
    )

    /** 비밀번호 인코더. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /** 로그인 컨트롤러가 직접 인증하도록 인증 관리자를 빈으로 등록한다. */
    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager =
        configuration.authenticationManager

    /**
     * 기동할 때 고정 계정을 사용자로 등록한다.
     * 한 번도 로그인하지 않은 계정에도 이메일로 초대를 보낼 수 있어야 해서 미리 만든다.
     */
    @Bean
    fun localAccountSeeder(userRepository: UserRepository): ApplicationRunner = ApplicationRunner {
        accounts.forEach { account ->
            val user = userRepository.findByProviderAndProviderId(PROVIDER, account.username)
                ?.apply {
                    email = account.email
                    name = account.name
                }
                ?: UserEntity(
                    provider = PROVIDER,
                    providerId = account.username,
                    email = account.email,
                    name = account.name,
                )
            userRepository.save(user)
        }
    }

    /** 아이디로 고정 계정을 찾아 사용자 id 를 붙인 principal 을 만든다. 없는 계정이면 `UsernameNotFoundException`. */
    @Bean
    fun userDetailsService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService {
        // 계정이 고정이라 해시는 한 번만 계산한다.
        val encodedPasswords = accounts.associate { it.username to requireNotNull(passwordEncoder.encode(it.password)) }

        return UserDetailsService { username ->
            val account = accounts.find { it.username == username }
                ?: throw UsernameNotFoundException("존재하지 않는 계정입니다.")
            val user = userRepository.findByProviderAndProviderId(PROVIDER, account.username)
                ?: throw UsernameNotFoundException("존재하지 않는 계정입니다.")

            LoginUserDetails(
                username = account.username,
                password = requireNotNull(encodedPasswords[account.username]),
                userId = requireNotNull(user.id),
            )
        }
    }

    companion object {
        /** 네이버 계정과 구분되는 임시 로그인 수단 식별자. */
        const val PROVIDER = "local"
    }
}
