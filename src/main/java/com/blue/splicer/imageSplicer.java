package com.blue.splicer;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

public class imageSplicer{

    private static int imageNum = 1; // start numbering from 1
    private static final String OUTPUT_DIR = "project/spliced";

    private static JTextArea logArea;
    private static JLabel imagePreview;
    private static BufferedImage lastClipboardImage = null;

    public static void main(String[] args) {
        ensureOutputDir();
        SwingUtilities.invokeLater(imageSplicer::createAndShowGUI);
        startClipboardListener();
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Bible Image Splicer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 600);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // Image preview
        imagePreview = new JLabel("Clipboard image will appear here", SwingConstants.CENTER);
        JScrollPane imageScroll = new JScrollPane(imagePreview);

        // Buttons
        JButton saveBtn = new JButton("Save Image");
        JButton redoBtn = new JButton("Redo This Page");
        JButton goToImageBtn = new JButton("Go To Image");

        // Log area
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Layout
        frame.add(imageScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveBtn);
        buttonPanel.add(redoBtn);
        buttonPanel.add(goToImageBtn);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(logScroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        saveBtn.addActionListener(e -> saveCurrentImage());

        redoBtn.addActionListener(e -> {
            if (imageNum > 1) {
                imageNum--;
                logArea.append("Redoing Image " + imageNum + "...\n");
            } else {
                logArea.append("Already at first image, cannot redo.\n");
            }
        });

        goToImageBtn.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(frame, "Enter image number:", imageNum);
            if (input != null) {
                try {
                    int newNum = Integer.parseInt(input.trim());
                    if (newNum > 0) {
                        imageNum = newNum;
                        logArea.append("Jumped to Image " + imageNum + "...\n");
                    } else {
                        JOptionPane.showMessageDialog(frame, "Image number must be positive.");
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid number entered.");
                }
            }
        });

        frame.setVisible(true);
    }

    private static void saveCurrentImage() {
        if (lastClipboardImage == null) {
            JOptionPane.showMessageDialog(null, "No image in clipboard!");
            return;
        }

        String fileName = OUTPUT_DIR + File.separator + imageNum + ".png";
        try {
            ImageIO.write(lastClipboardImage, "png", new File(fileName));
            logArea.append("Saved: " + fileName + "\n");
            imageNum++;
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to save image!");
        }
    }

    private static void startClipboardListener() {
        Thread clipboardThread = new Thread(() -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            while (true) {
                try {
                    if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                        Image img = (Image) clipboard.getData(DataFlavor.imageFlavor);
                        if (img != null) {
                            BufferedImage bufferedImage = toBufferedImage(img);
                            if (!imagesEqual(lastClipboardImage, bufferedImage)) {
                                lastClipboardImage = bufferedImage;
                                ImageIcon icon = new ImageIcon(bufferedImage.getScaledInstance(
                                        400, -1, Image.SCALE_SMOOTH));
                                SwingUtilities.invokeLater(() -> {
                                    imagePreview.setIcon(icon);
                                    imagePreview.setText(null);
                                    logArea.append("New image captured from clipboard\n");
                                });
                            }
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

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bimage = new BufferedImage(
                img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bimage.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return bimage;
    }

    private static boolean imagesEqual(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) return false;
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight())
            return false;
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) return false;
            }
        }
        return true;
    }
}
