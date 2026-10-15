# RC33 social follow lists and Bale manager verification

- Profile and account links lead to separate followers/following pages with pagination and authenticated pp_follow_list API.
- Follow/unfollow submits to existing pp_follow_toggle POST action.
- Admin user screen queries existing verified bale_connected_users API to mark the status per user. This is an interim lookup until the server offers bulk linkage status. A missing response is displayed as unknown, not falsely disconnected.
- Server pp_follow_list endpoint and member-level authorization are not confirmed in production; API errors are explicit.
