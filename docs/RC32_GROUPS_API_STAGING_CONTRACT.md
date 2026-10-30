# RC32 — Groups, invites, chat and score client integration (STAGING CONTRACT ONLY)

UI surfaces: Home > Groups > Create/manage, group detail, invite by username, invitation acceptance/rejection, invitation URL, scheduled events, separate group chat, score breakdown.
Android operations, all authenticated PHP session, POST with CSRF:
- GET pp_groups_list -> {ok,groups:[{id,name,description,visibility,viewer_member,viewer_admin,can_join,member_count,total_points}], events:[], invitations:[{id,group_name,status}]}
- GET pp_group_detail?group_id=N -> {ok,group:{id,...},members:[{display_name,is_admin}],events:[]}
- POST pp_group_create {name,description,visibility}
- POST pp_group_join {group_id}
- POST pp_group_invite {group_id,username}
- POST pp_group_invite_answer {invitation_id,accept}
- POST pp_group_invite_link {group_id} -> {invite_url:"https://pedalpro.ir/..."}, never create invite tokens on Android
- POST pp_group_event_save {group_id,title,event_date,meeting_location,request_id}
- GET pp_group_messages?group_id=N -> {messages:[{id,sender_name,body,created_at}]}
- POST pp_group_message_send {group_id,body,client_nonce}
- GET pp_group_points?group_id=N -> {total_points,components:[{title,points}]}

SECURITY REQUIREMENTS BEFORE ENABLING:
1. Strict PHP login, membership, admin and request-group ACL on each endpoint; never use client viewer_admin as authorization.
2. GET only returns public groups or private groups where caller belongs. Messages and invite tokens must never leak to nonmembers.
3. Make message client_nonce and event request_id idempotent; rate limit invite, group and chat actions; sanitize text and links.
4. Send Bale messages from server via existing write-only credential queue after confirmed event creation; name group explicitly.
5. Preserve membership history and separate personal/group points ledger without duplicate counting; do not award scores until policy is approved.
6. Existing PHP/MySQL source and staged migrations are unavailable in this session. No server-side endpoint or production deployment has been verified.

Android deliberately reports endpoint failures rather than fabricating memberships or sending unauthorized messages.
