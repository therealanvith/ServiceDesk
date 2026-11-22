package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The assistant's on-device knowledge base: common office IT problems with safe steps anyone can
 * try. It needs no network or API key, so it backs the rule-based assistant and the chatbot when
 * Gemini is not available. Entries are matched by weighted keyword hits in the text.
 */
public final class KnowledgeBase {

  /**
   * @param keywords lower-case words or phrases; a longer phrase counts for more
   * @param escalate when to stop self-help and file a ticket
   */
  public record Article(
      String id,
      String title,
      Category category,
      List<String> keywords,
      String summary,
      List<String> steps,
      String escalate) {}

  private static final List<Article> ARTICLES =
      List.of(
          new Article(
              "wifi",
              "Wi-Fi won't connect or keeps dropping",
              Category.NETWORK,
              List.of(
                  "wifi", "wi-fi", "wireless", "no internet", "keeps disconnecting", "dropping"),
              "Most Wi-Fi problems clear after reconnecting to the network.",
              List.of(
                  "Turn Wi-Fi off and on again, or toggle airplane mode for ten seconds.",
                  "Choose the office network, select Forget, then reconnect and sign in again.",
                  "Restart your computer, then try a website that is not cached.",
                  "Move closer to an access point and check whether colleagues nearby are affected."),
              "If several people nearby are offline, report it as affecting a team."),
          new Article(
              "vpn",
              "VPN will not connect",
              Category.NETWORK,
              List.of("vpn", "remote access", "work from home", "tunnel"),
              "A VPN usually fails because of the local connection or a stale session.",
              List.of(
                  "Check that your home internet works by opening any public website.",
                  "Disconnect the VPN completely, wait a few seconds, and connect again.",
                  "Sign out of the VPN client and sign in again with your current password.",
                  "Restart the VPN client, then your computer, if it still fails."),
              "If you see an error code or are locked out, include the exact message in your ticket."),
          new Article(
              "slow-internet",
              "Internet or web pages are slow",
              Category.NETWORK,
              List.of(
                  "slow internet",
                  "slow network",
                  "pages load slowly",
                  "buffering",
                  "lag",
                  "latency"),
              "Slowness is often one device, one browser or one busy connection.",
              List.of(
                  "Close tabs and apps that stream or download in the background.",
                  "Try a second website and a second device to see whether it is only your computer.",
                  "Restart your browser and clear its cached images and files.",
                  "Move to a wired connection or closer to the access point."),
              "If the whole area is slow, report it as affecting a team with the time it began."),
          new Article(
              "ethernet",
              "Wired network port has no connection",
              Category.NETWORK,
              List.of("ethernet", "network cable", "lan port", "wall port", "no network"),
              "A loose cable or dead port is the usual cause.",
              List.of(
                  "Unplug and firmly re-seat the cable at both ends.",
                  "Try a different cable or a different wall port, if there is one.",
                  "Check that the port lights blink; no light means the cable or port is dead.",
                  "Restart your computer so it asks for a new address."),
              "Report the wall-port label, which helps staff find a dead port quickly."),
          new Article(
              "printer-offline",
              "Printer is offline or will not print",
              Category.HARDWARE,
              List.of(
                  "printer",
                  "printing",
                  "print job",
                  "offline printer",
                  "won't print",
                  "cannot print"),
              "A stuck queue or a paused printer is the usual cause.",
              List.of(
                  "Check that the printer is on, has paper and shows no error on its screen.",
                  "Open the print queue, cancel stuck jobs and print one test page.",
                  "Pick the printer again in your print dialog and make sure it is not set to offline.",
                  "Restart the printer, wait for it to be ready, and print again."),
              "Include the printer name or label and any message on its screen."),
          new Article(
              "paper-jam",
              "Paper jam or poor print quality",
              Category.HARDWARE,
              List.of("paper jam", "jammed", "streaks", "faded print", "toner", "ink", "smudge"),
              "Jams and streaks can often be cleared safely by hand.",
              List.of(
                  "Switch the printer off and open the trays and rear door as shown on the printer.",
                  "Pull jammed paper out slowly in the direction of travel, without tearing it.",
                  "Fan the paper before reloading it and check it is not damp or creased.",
                  "Run a cleaning page from the printer menu if lines or streaks remain."),
              "Do not reach into hot parts; ask for help if paper will not come out."),
          new Article(
              "password",
              "Forgotten or expired password",
              Category.ACCESS,
              List.of(
                  "password",
                  "forgot password",
                  "forgotten password",
                  "reset password",
                  "password expired",
                  "wrong password",
                  "can't log in",
                  "cannot log in",
                  "login"),
              "You can usually reset your own password without help.",
              List.of(
                  "Check Caps Lock and type the password slowly into a visible field if available.",
                  "Use Forgot password on the sign-in page to get a reset link.",
                  "Sign out completely, close the browser and try again in a private window.",
                  "Make sure you are using your work username, not a personal account."),
              "If the reset email never arrives, file a ticket so staff can verify and reset it."),
          new Article(
              "locked",
              "Account locked",
              Category.ACCESS,
              List.of("locked out", "account locked", "too many attempts", "account disabled"),
              "Accounts lock after repeated wrong passwords and often unlock on their own.",
              List.of(
                  "Wait about 15 minutes without trying again; repeated attempts restart the timer.",
                  "Then sign in once with the password you are sure of.",
                  "If you changed your password recently, update it on your phone and other devices too."),
              "If you are still locked after 30 minutes, file a ticket with the time it started."),
          new Article(
              "mfa",
              "Verification code or authenticator problems",
              Category.ACCESS,
              List.of(
                  "mfa",
                  "2fa",
                  "authenticator",
                  "verification code",
                  "two-factor",
                  "one time code",
                  "otp"),
              "Codes fail mostly because of the phone's clock or an old registration.",
              List.of(
                  "Turn on automatic date and time on your phone, then try the newest code.",
                  "Do not reuse a code; wait for the next one to appear.",
                  "If you got a new phone, you must register the authenticator again."),
              "If you lost your phone, file a ticket right away so the old device can be removed."),
          new Article(
              "permission",
              "Access denied to a file, folder or system",
              Category.ACCESS,
              List.of(
                  "permission denied",
                  "access denied",
                  "no access",
                  "not authorised",
                  "not authorized",
                  "403",
                  "shared drive",
                  "network folder",
                  "sharepoint"),
              "Access is granted per person, so a missing permission usually needs approval.",
              List.of(
                  "Confirm you are signed in with your own work account.",
                  "Sign out and in again; new permissions only apply to fresh sessions.",
                  "Check the path or link for typos and that the folder was not moved."),
              "File a ticket naming the exact folder or system, and who in your team can approve it."),
          new Article(
              "email-sync",
              "Email not sending, receiving or syncing",
              Category.SOFTWARE,
              List.of(
                  "email",
                  "outlook",
                  "mailbox",
                  "not receiving",
                  "inbox",
                  "not syncing",
                  "send failed"),
              "Mail problems are commonly a stale connection or a full mailbox.",
              List.of(
                  "Check that you are online and that the mail app shows Connected.",
                  "Close and reopen the mail app, then press Send/Receive.",
                  "Check your mailbox size and empty deleted items and large attachments.",
                  "Try webmail in a browser; if it works there, only the app needs a refresh."),
              "If webmail also fails, include any error code in your ticket."),
          new Article(
              "calendar",
              "Calendar invites or meetings missing",
              Category.SOFTWARE,
              List.of("calendar", "invite", "meeting invite", "not showing meeting"),
              "Calendar gaps are mostly sync delays.",
              List.of(
                  "Refresh or restart the calendar app and check the correct calendar is ticked.",
                  "Open the calendar in a browser to see whether the meeting appears there.",
                  "Ask the organiser to resend the invite."),
              "Mention the meeting title and organiser if you file a ticket."),
          new Article(
              "video-call",
              "Camera or microphone not working in calls",
              Category.SOFTWARE,
              List.of(
                  "camera",
                  "webcam",
                  "microphone",
                  "mic",
                  "teams",
                  "zoom",
                  "meet",
                  "no sound in call",
                  "can't hear",
                  "cannot hear",
                  "headset"),
              "Call apps need permission to use the camera and microphone.",
              List.of(
                  "In the call app settings, choose the right camera, microphone and speaker.",
                  "Check the operating system privacy settings allow the app to use them.",
                  "Unplug and replug a USB headset or camera and check any mute switch on it.",
                  "Close other apps that may hold the camera, then restart the call app."),
              "Say which app and which device you use, so staff can reproduce it."),
          new Article(
              "audio",
              "No sound from the computer",
              Category.HARDWARE,
              List.of("no sound", "speakers", "volume", "audio", "sound not working"),
              "Sound problems often come from the wrong output device or a muted setting.",
              List.of(
                  "Check the volume and mute button on the computer and on the speakers.",
                  "Pick the correct output device in the sound settings.",
                  "Replug headphones or speakers, then restart the computer if it is still silent."),
              "If no device shows up at all, mention the model."),
          new Article(
              "display",
              "Monitor shows no picture",
              Category.HARDWARE,
              List.of(
                  "monitor",
                  "screen is black",
                  "black screen",
                  "no display",
                  "no signal",
                  "second screen",
                  "external display",
                  "projector",
                  "flickering"),
              "A loose cable or the wrong input is the usual cause.",
              List.of(
                  "Check the monitor is on and set to the right input (HDMI, DisplayPort).",
                  "Re-seat the cable at both ends, or try another cable or port.",
                  "Press Windows+P and choose Extend or Duplicate to wake an external screen.",
                  "Restart the computer with the monitor already connected."),
              "Note the monitor model and whether a light is on."),
          new Article(
              "keyboard-mouse",
              "Keyboard or mouse not responding",
              Category.HARDWARE,
              List.of(
                  "keyboard",
                  "mouse",
                  "touchpad",
                  "trackpad",
                  "cursor",
                  "keys not working",
                  "typing"),
              "Input devices fail from batteries, connections or a sleeping receiver.",
              List.of(
                  "Replace or recharge the batteries and switch the device off and on.",
                  "Unplug the USB receiver or cable and plug it into another port.",
                  "For Bluetooth, remove the device in settings and pair it again.",
                  "Restart the computer if even a different device does not respond."),
              "If a laptop's built-in keyboard fails, report it so a repair can be arranged."),
          new Article(
              "boot",
              "Computer will not start",
              Category.HARDWARE,
              List.of(
                  "won't turn on",
                  "will not turn on",
                  "won't start",
                  "won't boot",
                  "not booting",
                  "no power",
                  "dead laptop",
                  "stuck on logo"),
              "No start usually means power, not a broken computer.",
              List.of(
                  "Check the power cable and socket, and try another socket.",
                  "Unplug everything, including USB drives and docks, hold the power button for 15 seconds, then start it.",
                  "Leave a laptop on its charger for 30 minutes before trying again.",
                  "Do not open the case or reinstall anything yourself."),
              "File a ticket as urgent if you cannot work; staff can arrange a loan device."),
          new Article(
              "battery",
              "Laptop battery drains fast or will not charge",
              Category.HARDWARE,
              List.of("battery", "charging", "charger", "not charging", "drains"),
              "Chargers and cables wear out more often than batteries.",
              List.of(
                  "Try another wall socket and check the charger plug is firmly seated.",
                  "Look for damage on the cable and check the charging light.",
                  "Lower the screen brightness and close heavy apps while on battery."),
              "Mention the charger label, as a replacement charger may be needed."),
          new Article(
              "slow-pc",
              "Computer is slow or freezing",
              Category.SOFTWARE,
              List.of(
                  "slow computer",
                  "slow pc",
                  "slow laptop",
                  "freezing",
                  "frozen",
                  "hangs",
                  "not responding",
                  "very slow",
                  "takes forever"),
              "A restart and closing heavy programs fixes most slowness.",
              List.of(
                  "Save your work and restart the computer; a long uptime is a common cause.",
                  "Close unused programs and browser tabs.",
                  "Make sure at least 10% of the disk is free and pending updates are installed.",
                  "Note whether one program or everything is slow."),
              "If it is slow every day, a ticket helps staff check the device's health."),
          new Article(
              "crash",
              "Application crashes or shows an error",
              Category.SOFTWARE,
              List.of(
                  "crash",
                  "crashes",
                  "keeps closing",
                  "error message",
                  "stopped working",
                  "won't open",
                  "application error"),
              "Crashes often follow an update or a corrupted setting.",
              List.of(
                  "Write down or take a screenshot of the exact error message.",
                  "Close the application fully and open it again.",
                  "Install pending updates for the application and the system, then restart.",
                  "Try the same task with a smaller file or a new document."),
              "Attach the screenshot and the steps that cause it to your ticket."),
          new Article(
              "browser",
              "Website or web app misbehaves",
              Category.SOFTWARE,
              List.of(
                  "browser",
                  "chrome",
                  "edge",
                  "firefox",
                  "website",
                  "web page",
                  "page not loading",
                  "blank page",
                  "cache"),
              "Stale cached data is a frequent cause for a page that worked before.",
              List.of(
                  "Reload the page with Ctrl+Shift+R.",
                  "Open it in a private window to rule out extensions and old cookies.",
                  "Clear cached images and files for the last 24 hours and try again.",
                  "Try a second browser."),
              "Include the web address and the browser you used."),
          new Article(
              "install",
              "Software install or licence request",
              Category.SOFTWARE,
              List.of(
                  "install",
                  "software request",
                  "license",
                  "licence",
                  "activation",
                  "need access to software",
                  "new software"),
              "Software is installed by the service desk so that it stays licensed and safe.",
              List.of(
                  "Check the company software portal or app store for an approved version.",
                  "Do not download installers from the web yourself.",
                  "If it is not listed, request it with the name, version and why you need it."),
              "File a ticket with your manager's name if a purchase needs approval."),
          new Article(
              "update-stuck",
              "System update stuck or keeps restarting",
              Category.SOFTWARE,
              List.of(
                  "update stuck",
                  "updating",
                  "windows update",
                  "restart loop",
                  "keeps restarting",
                  "pending update"),
              "Updates can take a while and should be left alone.",
              List.of(
                  "Give the update up to an hour on a charger before taking action.",
                  "Do not hold the power button during an update unless it has been stuck for hours.",
                  "Once it finishes, restart once more to complete the install."),
              "If it loops after a manual restart, report it as blocking."),
          new Article(
              "storage",
              "Disk full or cannot save files",
              Category.SOFTWARE,
              List.of(
                  "disk full",
                  "storage full",
                  "no space",
                  "low disk",
                  "cannot save",
                  "can't save",
                  "out of space"),
              "A full disk stops saving, updating and even sign-in.",
              List.of(
                  "Empty the recycle bin and delete large downloads you no longer need.",
                  "Move big files to the shared drive or cloud storage.",
                  "Uninstall programs you do not use."),
              "File a ticket if you cannot free space, so more storage can be arranged."),
          new Article(
              "file-recovery",
              "Deleted or overwritten file",
              Category.SOFTWARE,
              List.of(
                  "deleted file",
                  "lost file",
                  "recover file",
                  "overwrote",
                  "previous version",
                  "missing file",
                  "accidentally deleted"),
              "Many files can be restored if you act before saving anything new.",
              List.of(
                  "Check the recycle bin first and restore the file from there.",
                  "On the shared drive or cloud storage, open Version history or Restore previous versions.",
                  "Stop saving over the file until you have checked."),
              "Tell staff the file name, location and when it was last fine."),
          new Article(
              "usb-bluetooth",
              "USB device or Bluetooth not detected",
              Category.HARDWARE,
              List.of("usb", "not detected", "bluetooth", "dock", "external drive", "pair"),
              "Detection issues are often a bad port or a stuck driver.",
              List.of(
                  "Try another port and cable, without using a hub.",
                  "For Bluetooth, turn it off and on and remove then pair the device again.",
                  "Restart the computer with the device connected."),
              "Give the device make and model."),
          new Article(
              "phishing",
              "Suspicious email or message",
              Category.ACCESS,
              List.of(
                  "phishing",
                  "suspicious",
                  "scam",
                  "spam",
                  "fake email",
                  "clicked a link",
                  "virus",
                  "malware",
                  "hacked",
                  "ransomware"),
              "Treat anything unexpected as unsafe, and report it quickly.",
              List.of(
                  "Do not click links, open attachments or reply to the message.",
                  "If you already clicked or typed a password, change that password now from a trusted device.",
                  "Disconnect from the network if a file or program behaves strangely, and do not shut down."),
              "Report it immediately as urgent, even if you are unsure whether it did any harm."),
          new Article(
              "phone-mail",
              "Work email or apps on a phone",
              Category.SOFTWARE,
              List.of("phone", "mobile", "android", "iphone", "company portal", "mdm"),
              "Phone sign-ins break when a password changes or a profile expires.",
              List.of(
                  "Update the saved password in the mail or work app on your phone.",
                  "Sign out of the work app and sign in again.",
                  "Check that the phone is online and the system is up to date."),
              "Say which phone and system version you have."));

  private KnowledgeBase() {}

  public static List<Article> articles() {
    return ARTICLES;
  }

  /** The best-matching article, if any keyword matched. */
  public static Optional<Article> best(String text) {
    return search(text, 1).stream().findFirst();
  }

  /** Up to {@code limit} articles, best first; articles with no match are left out. */
  public static List<Article> search(String text, int limit) {
    if (text == null || text.isBlank() || limit <= 0) {
      return List.of();
    }
    String haystack = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9'+-]+", " ") + " ";
    record Scored(Article article, int score) {}
    return ARTICLES.stream()
        .map(a -> new Scored(a, score(a, haystack)))
        .filter(s -> s.score() > 0)
        .sorted(Comparator.comparingInt(Scored::score).reversed())
        .limit(limit)
        .map(Scored::article)
        .toList();
  }

  /** Whole-word match; longer keywords may also match as a word start ("printers"). */
  private static boolean matches(String haystack, String keyword) {
    String needle = " " + keyword;
    int from = 0;
    while (true) {
      int at = haystack.indexOf(needle, from);
      if (at < 0) {
        return false;
      }
      int end = at + needle.length();
      if (keyword.length() >= 5 || haystack.charAt(end) == ' ') {
        return true;
      }
      from = at + 1;
    }
  }

  private static int score(Article article, String haystack) {
    int total = 0;
    for (String keyword : article.keywords()) {
      if (matches(haystack, keyword)) {
        total += keyword.contains(" ") ? 3 : 2;
      }
    }
    return total;
  }
}
