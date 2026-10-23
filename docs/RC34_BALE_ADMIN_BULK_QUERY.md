# Admin Bale bulk lookup
Replaced per-user API calls in user settings with a bounded paginated authenticated bale_connected_users lookup. Linked users display connected. Unconfirmed pagination returns unknown rather than incorrectly reporting disconnected. No credentials are exposed.
