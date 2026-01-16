# Authentication

Currently, LogKitty does **not** require user authentication. It is a local developer tool.

## Future Considerations
If cloud features (e.g., uploading logs to a pastebin or syncing configs) are added:
- OAuth2 via GitHub or Google would be the preferred method.
- Tokens should be stored securely using `EncryptedSharedPreferences`.
