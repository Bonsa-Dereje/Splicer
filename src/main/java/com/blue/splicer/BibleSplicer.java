package com.blue.splicer;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

public class BibleSplicer {

    private static int pdfNum = 1;
    private static int chapterNum = 1;
    private static final String OUTPUT_DIR = "C:\\Users\\boni\\Desktop\\Files\\The Bible Project\\spliced";
    private static final String NIYALA_FONT_PATH = "C:\\Users\\boni\\Desktop\\Files\\The Bible Project\\splicer\\splicer\\Niyala.ttf";

    private static JTextArea logArea;
    private static JTextArea textArea;
    private static String lastClipboardText = "";

    public static void main(String[] args) {
        ensureOutputDir();
        SwingUtilities.invokeLater(BibleSplicer::createAndShowGUI);
        startClipboardListener();
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Bible PDF Splicer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 600);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel
        JPanel topPanel = new JPanel();
        JButton minusBtn = new JButton("-");
        JLabel pdfNumLabel = new JLabel("PDF Number: " + pdfNum);
        JButton plusBtn = new JButton("+");
        topPanel.add(minusBtn);
        topPanel.add(pdfNumLabel);
        topPanel.add(plusBtn);

        // Text area
        textArea = new JTextArea(15, 50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        File fontFile = new File(NIYALA_FONT_PATH);
        if (fontFile.exists()) {
            try {
                Font nyalaFont = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(16f);
                textArea.setFont(nyalaFont);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Font file not found: " + NIYALA_FONT_PATH);
        }

        JScrollPane textScroll = new JScrollPane(textArea);

        // Buttons
        JButton saveBtn = new JButton("Save PDF");
        JButton redoBtn = new JButton("Redo This Chapter");
        JButton goToChapterBtn = new JButton("Go To Chapter");

        // Log area
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Layout
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(textScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveBtn);
        buttonPanel.add(redoBtn);
        buttonPanel.add(goToChapterBtn);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(logScroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        plusBtn.addActionListener(e -> {
            pdfNum++;
            chapterNum = 1;
            pdfNumLabel.setText("PDF Number: " + pdfNum);
        });

        minusBtn.addActionListener(e -> {
            if (pdfNum > 1) {
                pdfNum--;
                chapterNum = 1;
                pdfNumLabel.setText("PDF Number: " + pdfNum);
            }
        });

        saveBtn.addActionListener(e -> saveCurrentChapter());

        redoBtn.addActionListener(e -> {
            if (chapterNum > 1) {
                chapterNum--;
                textArea.setText("");
                logArea.append("Redoing Chapter " + chapterNum + "...\n");
            } else {
                logArea.append("Already at first chapter, cannot redo.\n");
            }
        });

        goToChapterBtn.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(frame, "Enter chapter number:", chapterNum);
            if (input != null) {
                try {
                    int newChapter = Integer.parseInt(input.trim());
                    if (newChapter > 0) {
                        chapterNum = newChapter;
                        textArea.setText("");
                        logArea.append("Starting from Chapter " + chapterNum + "...\n");
                    } else {
                        JOptionPane.showMessageDialog(frame, "Chapter number must be positive.");
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid number entered.");
                }
            }
        });

        frame.setVisible(true);
    }

    private static void saveCurrentChapter() {
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Text area is empty!");
            return;
        }

        String fileName = OUTPUT_DIR + File.separator + pdfNum + "_" + chapterNum + ".pdf";
        boolean success = saveTextToPdf(text, fileName);
        if (success) {
            File f = new File(fileName);
            logArea.append(String.format("Saved: %s (%d KB)\n", f.getName(), f.length() / 1024));
            chapterNum++;
            textArea.setText("");
        } else {
            JOptionPane.showMessageDialog(null, "Failed to save PDF!");
        }
    }

    private static void startClipboardListener() {
        Thread clipboardThread = new Thread(() -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            while (true) {
                try {
                    Transferable contents = clipboard.getContents(null);
                    if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                        if (!text.equals(lastClipboardText)) {
                            lastClipboardText = text;
                            SwingUtilities.invokeLater(() -> textArea.append(text + "\n"));
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        clipboardThread.setDaemon(true);
        clipboardThread.start();
    }

    private static void ensureOutputDir() {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Failed to create output directory!");
        }
    }

    // --------- FIXED SECTION: properly adds more pages -----------
    private static boolean saveTextToPdf(String text, String filePath) {
        try (PDDocument document = new PDDocument()) {
            File fontFile = new File(NIYALA_FONT_PATH);
            if (!fontFile.exists()) {
                JOptionPane.showMessageDialog(null, "Nyala font not found at " + fontFile.getAbsolutePath());
                return false;
            }
            PDType0Font font = PDType0Font.load(document, fontFile);

            float margin = 50;
            float leading = 16;
            float pageWidth = PDRectangle.A4.getWidth() - 2 * margin;
            float pageHeight = PDRectangle.A4.getHeight() - 2 * margin;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(font, 12);
            contentStream.beginText();
            float yPosition = PDRectangle.A4.getHeight() - margin;
            contentStream.newLineAtOffset(margin, yPosition);

            String[] paragraphs = text.split("\n");
            for (String paragraph : paragraphs) {
                String[] words = paragraph.split(" ");
                StringBuilder lineBuilder = new StringBuilder();
                for (String word : words) {
                    String tempLine = lineBuilder.length() == 0 ? word : lineBuilder + " " + word;
                    float textWidth = font.getStringWidth(tempLine) / 1000 * 12;
                    if (textWidth > pageWidth) {
                        // write current line
                        contentStream.showText(lineBuilder.toString());
                        contentStream.newLineAtOffset(0, -leading);
                        yPosition -= leading;
                        if (yPosition <= margin) {
                            contentStream.endText();
                            contentStream.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            contentStream = new PDPageContentStream(document, page);
                            contentStream.setFont(font, 12);
                            contentStream.beginText();
                            yPosition = PDRectangle.A4.getHeight() - margin;
                            contentStream.newLineAtOffset(margin, yPosition);
                        }
                        lineBuilder = new StringBuilder(word);
                    } else {
                        if (lineBuilder.length() > 0) lineBuilder.append(" ");
                        lineBuilder.append(word);
                    }
                }
                if (lineBuilder.length() > 0) {
                    contentStream.showText(lineBuilder.toString());
                    contentStream.newLineAtOffset(0, -leading);
                    yPosition -= leading;
                    if (yPosition <= margin) {
                        contentStream.endText();
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.setFont(font, 12);
                        contentStream.beginText();
                        yPosition = PDRectangle.A4.getHeight() - margin;
                        contentStream.newLineAtOffset(margin, yPosition);
                    }
                }
            }

            contentStream.endText();
            contentStream.close();

            document.save(new File(filePath));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
