-- Profile picture URL, taken from the Google account at sign-in (null for password accounts).
ALTER TABLE users ADD COLUMN avatar TEXT;
