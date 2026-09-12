package com.mallucupid.app.data.remote

import com.mallucupid.app.BuildConfig

/**
 * Supabase project configuration.
 *
 * The anon/publishable key is safe to ship in the client — it is protected by
 * Row-Level Security policies defined in supabase/migrations/0001_initial_schema.sql.
 * The service-role key is NEVER referenced from the app.
 */
object SupabaseConfig {
    const val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    const val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    val REST_BASE: String get() = "$SUPABASE_URL/rest/v1"
    val AUTH_BASE: String get() = "$SUPABASE_URL/auth/v1"
    val FUNCTIONS_BASE: String get() = "$SUPABASE_URL/functions/v1"
}
