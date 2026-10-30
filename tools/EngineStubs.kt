package com.killswitch.aura

// Stand-ins so the engine compiles and is tested without Compose. Never shipped: the real activities are in app/src/main.
class MainActivity : android.app.Activity()
class RingActivity : android.app.Activity() { companion object { var cur: RingActivity? = null } }
