package mailsent.check.SentMailChecker.service;


import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

@Service
public class GmailAutomationService {

    private static final String APPLICATION_NAME = "SentMailCheckerApplication";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final Logger logger = LoggerFactory.getLogger(GmailAutomationService.class);


    private Gmail service;

    public GmailAutomationService() throws IOException, GeneralSecurityException {
        this.service = getGmailService();
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        // Load client secrets.
        InputStream in = GmailAutomationService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY,
                com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in)),
                SCOPES)
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                new AuthorizationCodeInstalledApp(flow, receiver).authorize("user"))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Check if an email has been sent to the specified address within the user's Sent Mail.
     * @param email Target recipient email address
     * @return true if sent email found, false otherwise
     */
    public boolean checkEmailSent(String email) {
        try {
            final String user = "me";
            String query = "in:sent " + email;  // less strict

            ListMessagesResponse response = service.users().messages().list(user).setQ(query).execute();
            List<Message> messages = response.getMessages();

            if (messages != null && !messages.isEmpty()) {
                System.err.println("❌ Email Already Sent for: " + email);
                return true;
            } else {
                System.out.println("✅ Email Not sent for: " + email);
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Failed Gmail check for: {}", email, e);
            return false;
        }
    }
}
