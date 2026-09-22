package com.yong.travel.auth.config

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.repository.UserRepository
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
 * 네이버 OAuth 가 준비되기 전까지 쓰는 임시 인메모리 로그인 구성 (명세 §2.1).
 *
 * **`local`·`dev` 프로파일에서만 등록한다.** 비밀번호가 코드에 박힌 고정 계정이라
 * 운영 환경에 열려서는 안 된다. prod 에는 이 구성이 없으므로 로그인 수단 자체가 없다.
 *
 * OAuth 복구 시에는 이 파일과 `LocalLoginController` 를 들어내면 된다 (명세 §8.1).
 */
@Configuration
@Profile("local", "dev")
class LocalLoginConfig {

    /**
     * 고정 계정 목록. 그룹 공유와 초대를 실제로 주고받아 보려면 계정이 여럿이어야 해서 셋을 둔다.
     * 이메일은 초대 대상을 지정하는 열쇠다 (명세 §4.8).
     */
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

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager =
        configuration.authenticationManager

    /**
     * 기동 시 고정 계정을 `users` 에 upsert 한다.
     *
     * 로그인 시점에 만들지 않는 이유는 초대 때문이다 — 초대는 이메일로 상대를 찾으므로(명세 §4.8),
     * 상대가 한 번도 로그인하지 않았어도 행이 있어야 초대를 보낼 수 있다.
     */
    @Bean
    fun localAccountSeeder(userRepository: UserRepository): ApplicationRunner = ApplicationRunner {
        accounts.forEach { account ->
            val user = userRepository.findByProviderAndProviderId(PROVIDER, account.username)
                ?.apply {
                    email = account.email
                    name = account.name
                }
                ?: User(
                    provider = PROVIDER,
                    providerId = account.username,
                    email = account.email,
                    name = account.name,
                )
            userRepository.save(user)
        }
    }

    /**
     * 고정 계정에 DB 의 사용자 PK 를 붙여 principal 을 만든다.
     *
     * 계정 정보는 메모리에 있지만 사용자 PK 는 DB 에 있으므로, 둘을 여기서 잇는다.
     * 시더가 먼저 돌기 때문에 로그인 시점에는 행이 반드시 있다.
     */
    @Bean
    fun userDetailsService(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService {
        // 비밀번호는 빈을 만들 때 한 번만 해시한다. 계정이 고정이라 로그인마다 다시 계산할 이유가 없다.
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
        /** 네이버 계정("naver")과 구분되는 임시 로그인 수단 식별자다. */
        const val PROVIDER = "local"
    }
}
