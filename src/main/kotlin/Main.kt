package com.task.GitHubAuthorization

import GitHubUser
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.oauth
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.get
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.Routing
import kotlinx.html.*
import io.ktor.client.request.header
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.client.features.json.*
import io.ktor.http.content.resources
import io.ktor.routing.param
import io.ktor.http.content.static


@Location("/") class index()
@Location("/login/{type?}") class login(val type: String = "")

val loginGitHubProvider =
    OAuthServerSettings.OAuth2ServerSettings(
        name = "github",
        authorizeUrl = "https://github.com/login/oauth/authorize",
        accessTokenUrl = "https://github.com/login/oauth/access_token",
        clientId = "f2897097f947e4ad8b9b",
        clientSecret = "9fd55753f203a57b7b4aaf513edd206d5d8264a7"
    )

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(DefaultHeaders)
        install(CallLogging)
        install(Locations)
        install(Authentication) {
            oauth("gitHubOAuth") {
                client = HttpClient(Apache)
                providerLookup = { loginGitHubProvider }
                urlProvider = { p ->
                    redirectUrl(login(p.name), false) }
            }
        }

        install(Routing) {
            static("/static") {
                resources("static")
            }

            get<index> {
                call.loginPage()
            }

            authenticate("gitHubOAuth") {
                location<login>() {
                    param("error") {
                        handle {
                            call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                        }
                    }

                    handle {
                        val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                        val client = HttpClient(Apache) {
                            install(JsonFeature) {
                                serializer = JacksonSerializer()
                            }
                        }

                        val user: GitHubUser? = try {
                            when (principal) {
                                is OAuthAccessTokenResponse.OAuth1a -> client.get<GitHubUser>("https://api.github.com/user") {
                                    header("Authorization", "token ${principal.token}")
                                }
                                is OAuthAccessTokenResponse.OAuth2 -> client.get<GitHubUser>("https://api.github.com/user") {
                                    header("Authorization", "token ${principal.accessToken}")
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (user != null) {
                            call.loggedInSuccessResponse(user.login)
                        } else {
                            call.loginPage()
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
private fun <T : Any> ApplicationCall.redirectUrl(t: T, secure: Boolean = true): String {
    val hostPort = request.host() + request.port().let { port -> if (port == 80) "" else ":$port" }
    val protocol = when {
        secure -> "https"
        else -> "http"
    }
    return "$protocol://$hostPort${application.locations.href(t)}"
}

private suspend fun ApplicationCall.loginPage() {
    respondHtml {
        head {
            title { +"Login with GitHub" }
            styleLink("/static/styles.css")
        }
        body {
                p {
                    button {
                        onClick = "window.location.href='${application.locations.href(login(loginGitHubProvider.name))}'"
                        +"Sign in with GitHub"
                    }
                }
        }
    }
}

private suspend fun ApplicationCall.loginFailedPage(errors: List<String>) {
    respondHtml {
        head {
            title { +"Login with GitHub" }
            styleLink("/static/styles.css")
        }
        body {
            h1 {
                +"Login error"
            }

            for (e in errors) {
                p {
                    +e
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loggedInSuccessResponse(userLogin: String) {
    respondHtml {
        head {
            title { +"Logged in" }
            styleLink("/static/styles.css")
        }
        body {
            p {
                +"Successfull authorization, login: "
                b { +userLogin }
            }
        }
    }
}