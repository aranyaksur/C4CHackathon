import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.text.*;

public class WordFrequencySimplifier {

    private static final Map<Integer, String> wordPositions = new HashMap<>();

    public static void main(String[] args) {
        JFrame frame = new JFrame("Smart Word Difficulty Detector");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 400);

        JTextField inputField = new JTextField(40);
        JButton processButton = new JButton("Analyze");

        JTextPane outputPane = new JTextPane();
        outputPane.setEditable(false);
        StyledDocument doc = outputPane.getStyledDocument();

        // Define styles
        Style easyStyle = outputPane.addStyle("easy", null);
        StyleConstants.setForeground(easyStyle, Color.BLACK);

        Style mediumStyle = outputPane.addStyle("medium", null);
        StyleConstants.setForeground(mediumStyle, Color.ORANGE);
        StyleConstants.setUnderline(mediumStyle, true);

        Style hardStyle = outputPane.addStyle("hard", null);
        StyleConstants.setForeground(hardStyle, Color.BLUE);
        StyleConstants.setUnderline(hardStyle, true);

        // Layout
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Enter a sentence:"));
        topPanel.add(inputField);
        topPanel.add(processButton);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(outputPane), BorderLayout.CENTER);
        frame.setVisible(true);

        // Analyze button
        processButton.addActionListener(e -> {
            try {
                doc.remove(0, doc.getLength());
                wordPositions.clear();

                String sentence = inputField.getText();
                java.util.List<String> words = new ArrayList<>();

                // Handle double dash splitting
                for (String token : sentence.split(" ")) {
                    if (token.contains("--")) {
                        String[] parts = token.split("--");
                        for (String part : parts) {
                            if (!part.isEmpty()) words.add(part);
                        }
                    } else {
                        words.add(token);
                    }
                }

                int position = 0;
                for (String word : words) {
                    String clean = word.replaceAll("[^a-zA-Z\\-]", "").toLowerCase();

                    if (clean.isEmpty()) continue;

                    String difficulty = getWordDifficulty(clean);
                    Style style = switch (difficulty) {
                        case "hard" -> hardStyle;
                        case "medium" -> mediumStyle;
                        default -> easyStyle;
                    };

                    int start = doc.getLength();
                    doc.insertString(doc.getLength(), word + " ", style);

                    if (!difficulty.equals("easy")) {
                        wordPositions.put(start, clean);
                    }

                    position += word.length() + 1;
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
            }
        });

        // Click handler
        outputPane.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int offset = outputPane.viewToModel2D(e.getPoint());
                for (Map.Entry<Integer, String> entry : wordPositions.entrySet()) {
                    int start = entry.getKey();
                    int end = start + entry.getValue().length();
                    if (offset >= start && offset <= end + 1) {
                        String word = entry.getValue();
                        try {
                            String def = fetchDefinition(word);
                            JOptionPane.showMessageDialog(frame, word + ":\n" + def);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(frame, "Definition not found.");
                        }
                        break;
                    }
                }
            }
        });
    }

    // 🔍 Determine difficulty using Datamuse frequency
    private static String getWordDifficulty(String word) throws Exception {
        if (word.isEmpty()) return "easy";

        String apiUrl = "https://api.datamuse.com/words?sp=" + URLEncoder.encode(word, "UTF-8") + "&md=f";
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) return "easy";

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        Matcher matcher = Pattern.compile("\"tags\":\\[\"f:([0-9.]+)\"\\]").matcher(response.toString());
        if (matcher.find()) {
            double freq = Double.parseDouble(matcher.group(1));
            if (freq > 4.0) return "easy";
            else if (freq >= 2.5) return "medium";
            else return "hard";
        }

        return "easy";
    }

    // 📚 Get definition from dictionaryapi.dev
    private static String fetchDefinition(String word) throws Exception {
        String apiUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            throw new IOException("Word not found.");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            json.append(line);
        }
        reader.close();

        Matcher matcher = Pattern.compile("\"definition\":\"(.*?)\"").matcher(json.toString());
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "Definition not found.";
        }
    }
}
