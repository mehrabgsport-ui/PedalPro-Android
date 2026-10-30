# RC35 Group event map coordinates and invitation links
- Group event creation requires a native map-tapped valid start coordinate and passes it to the server.
- Native deep link accepts only https://pedalpro.ir/?group_invite=<48 hex>, displays confirmation before POST pp_group_invite_redeem.
- Server generates cryptographically random 24-byte tokens and stores SHA-256 only; never accepts tokens on GET.
- Requires staging deployment of separate private PHP/MySQL package and site deep-link routing; no production installation performed.
