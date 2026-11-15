package com.devicecare.app

enum class Mode { OFF, FEATURES, APP }

/**
 * How to recognise a screen.
 * ids      - a visible element whose view id contains one of these (lowercase) means "you are on this screen"
 * selected - a SELECTED tab/button whose label starts with one of these
 * hints    - a text box whose hint/text starts with one of these
 * texts    - a visible element whose text or description equals one of these exactly
 */
data class Sig(
    val ids: List<String> = emptyList(),
    val selected: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val texts: List<String> = emptyList()
)

data class AppDef(
    val key: String,
    val label: String,
    val note: String,
    val pkgs: List<String>,
    val dm: Sig? = null,
    val search: Sig? = null,
    val login: Sig? = null,
    val webDomains: List<String> = emptyList(),
    val defaultMode: Mode = Mode.OFF
) {
    val hasFeatures: Boolean get() = dm != null || search != null
}

/**
 * STARTING RULES. Social apps change their screens often, so expect to tune these
 * using the Debug recording in the app (see README).
 */
object Rules {

    val apps: List<AppDef> = listOf(
        AppDef(
            key = "instagram",
            label = "Instagram",
            note = "Direct messages, search & explore, sign-in screens",
            pkgs = listOf("com.instagram.android"),
            dm = Sig(
                ids = listOf(
                    "direct_inbox", "inbox_refreshable", "direct_thread",
                    "row_thread_composer", "direct_text_message", "direct_share"
                ),
                selected = listOf("Messages", "Direct"),
                hints = listOf("Message…", "Message...", "Send message")
            ),
            search = Sig(
                ids = listOf("action_bar_search_edit_text", "search_edit_text"),
                selected = listOf("Search and explore", "Search"),
                hints = listOf("Search")
            ),
            login = Sig(
                ids = listOf("login_username", "login_password"),
                texts = listOf(
                    "Log into existing account", "Create new account",
                    "Add Instagram account", "Add account", "Log in", "Sign up",
                    "Switch accounts"
                )
            ),
            webDomains = listOf("instagram.com"),
            defaultMode = Mode.FEATURES
        ),
        AppDef(
            key = "tiktok",
            label = "TikTok",
            note = "Inbox & messages, search, sign-in screens",
            pkgs = listOf(
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically.go"
            ),
            dm = Sig(
                selected = listOf("Inbox", "Messages"),
                hints = listOf("Send a message", "Message…", "Message...")
            ),
            search = Sig(
                selected = listOf("Search", "Discover"),
                hints = listOf("Search")
            ),
            login = Sig(
                texts = listOf(
                    "Log in to TikTok", "Sign up for TikTok", "Log in or sign up",
                    "Add account", "Use phone / email / username", "Continue with Google"
                )
            ),
            webDomains = listOf("tiktok.com"),
            defaultMode = Mode.FEATURES
        ),
        AppDef(
            key = "pinterest",
            label = "Pinterest",
            note = "Messages, search, sign-in screens",
            pkgs = listOf("com.pinterest"),
            dm = Sig(
                ids = listOf(
                    "conversation", "inbox_conversation", "message_composer", "send_message"
                ),
                selected = listOf("Inbox", "Messages"),
                hints = listOf("Send a message", "Add a message", "Message…")
            ),
            search = Sig(
                ids = listOf("typeahead", "search_bar_edit"),
                selected = listOf("Search"),
                hints = listOf("Search for ideas", "Search")
            ),
            login = Sig(
                texts = listOf(
                    "Log in", "Sign up", "Continue with Google",
                    "Continue with Facebook", "Add account"
                )
            ),
            webDomains = listOf("pinterest."),
            defaultMode = Mode.FEATURES
        ),
        AppDef(
            key = "snapchat",
            label = "Snapchat",
            note = "Chat, search, sign-in screens",
            pkgs = listOf("com.snapchat.android"),
            dm = Sig(
                selected = listOf("Chat"),
                hints = listOf("Send a chat", "Send a message")
            ),
            search = Sig(selected = listOf("Search"), hints = listOf("Search")),
            login = Sig(texts = listOf("Log In", "Sign Up", "Add account")),
            webDomains = listOf("snapchat.com")
        ),
        AppDef(
            key = "facebook",
            label = "Facebook",
            note = "Search & sign-in screens (block Messenger separately)",
            pkgs = listOf("com.facebook.katana"),
            search = Sig(hints = listOf("Search Facebook", "Search")),
            login = Sig(texts = listOf("Log in", "Create new account", "Add account")),
            webDomains = listOf("facebook.com")
        ),
        AppDef(
            key = "x",
            label = "X (Twitter)",
            note = "Messages, search, sign-in screens",
            pkgs = listOf("com.twitter.android"),
            dm = Sig(
                selected = listOf("Messages"),
                hints = listOf("Start a message", "Message")
            ),
            search = Sig(
                selected = listOf("Search and Explore", "Search"),
                hints = listOf("Search X", "Search")
            ),
            login = Sig(texts = listOf("Log in", "Create account", "Add an existing account")),
            webDomains = listOf("twitter.com")
        ),
        AppDef(
            key = "reddit",
            label = "Reddit",
            note = "Chat, search, sign-in screens",
            pkgs = listOf("com.reddit.frontpage"),
            dm = Sig(
                selected = listOf("Chat", "Inbox"),
                hints = listOf("Message", "Send a message")
            ),
            search = Sig(hints = listOf("Search Reddit", "Search")),
            login = Sig(texts = listOf("Log in", "Sign up", "Continue with Google")),
            webDomains = listOf("reddit.com")
        ),
        AppDef(
            key = "messenger",
            label = "Messenger",
            note = "Whole app",
            pkgs = listOf("com.facebook.orca")
        ),
        AppDef(
            key = "discord",
            label = "Discord",
            note = "Whole app",
            pkgs = listOf("com.discord"),
            webDomains = listOf("discord.com")
        ),
        AppDef(
            key = "whatsapp",
            label = "WhatsApp",
            note = "Whole app",
            pkgs = listOf("com.whatsapp"),
            webDomains = listOf("whatsapp.com")
        ),
        AppDef(
            key = "telegram",
            label = "Telegram",
            note = "Whole app",
            pkgs = listOf("org.telegram.messenger"),
            webDomains = listOf("telegram.org", "t.me")
        )
    )

    /** Browsers: hidden on a managed phone, and watched (best effort) otherwise. */
    val browsers: List<String> = listOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
        "com.brave.browser", "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "com.UCMobile.intl", "com.sonyericsson.browser"
    )

    /** Possible address-bar ids (appended to "<package>:id/"). */
    val urlBarIds: List<String> = listOf(
        "url_bar", "mozac_browser_toolbar_url_view", "url_edit_text", "location_bar_edit_text"
    )
}
