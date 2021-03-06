package org.hvdw.jexiftoolgui.controllers;

import com.twelvemonkeys.image.AffineTransformOp;

import org.hvdw.jexiftoolgui.MyConstants;
import org.hvdw.jexiftoolgui.MyVariables;
import org.hvdw.jexiftoolgui.Utils;
import org.hvdw.jexiftoolgui.facades.SystemPropertyFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;


import static org.hvdw.jexiftoolgui.facades.SystemPropertyFacade.SystemPropertyKey.LINE_SEPARATOR;

public class ImageFunctions {
    // Almost 100% copied from Dennis Damico's FastPhotoTagger
    // And he copied it almost 100% from Wyat Olsons original ImageTagger Imagefunctions (2005)
    // And I then extended it with the TwelveMonkeys imageIO libraries

    private final static ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ImageFunctions.class);

    public static int[] getbasicImageData (File file) {
        int[] basicdata = {0, 0, 999};
        long tmpvalue;
        String tmpValue;
        //Directory metadata = null;
        String filename = file.getName().replace("\\", "/");

        /*String[] readerFormatNames = ImageIO.getReaderFormatNames();
        for (String reader : readerFormatNames) {
            logger.info("reader {}", reader);
        }*/
        //logger.info("Now working on image: " +filename);
       /*String filenameExt = Utils.getFileExtension(filename);
        try {
            ImageInputStream input = ImageIO.createImageInputStream(file);

            try {
                // Get the reader
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

                if (!readers.hasNext()) {
                    logger.info("No reader for: {}", file.toString());
                    throw new IllegalArgumentException("No reader for: " + file);
                }

                ImageReader reader = readers.next();

                try {
                    reader.setInput(input);

                    ImageReadParam param = reader.getDefaultReadParam();
                    basicdata[0] = (int) reader.getWidth(0);
                    basicdata[1] = (int) reader.getHeight(0);
                    logger.info("Width {} Height {} AspectRatio {}", reader.getWidth(0), reader.getHeight(0), reader.getAspectRatio(0));
                    logger.info("metadata {}", reader.getStreamMetadata());

                } finally {
                    // Dispose reader in finally block to avoid memory leaks
                    reader.dispose();
                }
            } finally {
                // Close stream in finally block to avoid resource leaks
                input.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        // metadata extractor
        /*try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }
            /ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            for (Tag tag : directory.getTags()) {
                System.out.println(tag);
            }
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            for (Tag tag : directory.getTags()) {
                System.out.println(tag);
            }

        } catch (IOException | ImageProcessingException e) {
            e.printStackTrace();
        }*/

        try {
            BufferedImage bimg = ImageIO.read(file);
            int width = bimg.getWidth();
            int height = bimg.getHeight();
            basicdata[0] = bimg.getWidth();
            basicdata[1] = bimg.getHeight();
            logger.trace("bimg width {} height {}", bimg.getWidth(), bimg.getHeight());
        } catch (IOException e) {
            e.printStackTrace();
            logger.trace("bimg reading error {}", e);
        }
        // I can't solve it yet with TwelveMonkeys, so do it with exiftool
        String exiftool = Utils.platformExiftool();
        List<String> cmdparams = new ArrayList<String>();
        cmdparams.add(exiftool.trim());
        cmdparams.addAll(Arrays.asList(MyConstants.WIDTH_HEIGHT_ORIENTATION));
        cmdparams.add(file.getPath());
        int counter = 0;
        String who ="";

        try {
            who = CommandRunner.runCommand(cmdparams);
            logger.trace("res is {}", who);
        } catch (IOException | InterruptedException ex) {
            logger.error("Error executing command", ex);
        }

        if (who.length() > 0) {
            String[] lines = who.split(SystemPropertyFacade.getPropertyByKey(LINE_SEPARATOR));
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts[1].trim().matches("[0-9]+")) {
                    /*if ( (basicdata[0] == 0 && parts[0].contains("Width")) || (basicdata[1]  == 0 && parts[0].contains("Height")) ) {
                        logger.info("getbasicdata parts0 {} parts1 *{}*", parts[0], parts[1].trim());
                        basicdata[counter] = Integer.parseInt(parts[1].trim());
                    }*/
                    try {
                        if (parts[0].contains("Orientation")) {
                            basicdata[2] = Integer.parseInt(parts[1].trim());
                        }
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        logger.info("error Integer.parseInt {}", e);
                    }
                }
                counter++;
            }
        }
            return basicdata;
    }

    /*
    / This method is used to mass extract thumbnails from JPG images, either by load folder, load images or "dropped" images.
     */
    public static void extractThumbnails() {
        String exiftool = Utils.platformExiftool();
        List<String> cmdparams = new ArrayList<String>();
        cmdparams.add(exiftool.trim());

        boolean isWindows = Utils.isOsFromMicrosoft();
        File[] files = MyVariables.getSelectedFiles();

        // Get the temporary directory
        String tempWorkDir = MyVariables.gettmpWorkFolder();

        cmdparams.add("-a");
        cmdparams.add("-m");
        cmdparams.add("-b");
        cmdparams.add("-W");
        cmdparams.add(tempWorkDir + File.separator + "%f_%t%-c.%s");
        cmdparams.add("-preview:ThumbnailImage");
        //cmdparams.add("-preview:PreviewImage");

        for (File file : files) {
            if (isWindows) {
                cmdparams.add(file.getPath().replace("\\", "/"));
            } else {
                cmdparams.add(file.getPath());
            }
        }
        try {
            String cmdResult = CommandRunner.runCommand(cmdparams);
            //logger.info("cmd result from export previews for single RAW" + cmdResult);
        } catch (IOException | InterruptedException ex) {
            logger.error("Error executing command to export thumbnails and previews for selected images");
            //exportResult = (" " + ResourceBundle.getBundle("translations/program_strings").getString("ept.exporterror"));
        }

    }

    /*
     * This method is used to try to get a preview image for those (RAW) images that can't be converted directly to be displayed in the left images column
     * We will try to extract a jpg from the RAW to the tempdir and resize/display that one
     */
    public static String ExportPreviewsThumbnailsForIconDisplay(File file) {
        List<String> cmdparams = new ArrayList<String>();
        String exportResult = "Success";

        cmdparams.add(Utils.platformExiftool());
        boolean isWindows = Utils.isOsFromMicrosoft();

        // Get the temporary directory
        String tempWorkDir = MyVariables.gettmpWorkFolder();

        cmdparams.add("-a");
        cmdparams.add("-m");
        cmdparams.add("-b");
        cmdparams.add("-W");
        cmdparams.add(tempWorkDir + File.separator + "%f_%t%-c.%s");
        cmdparams.add("-preview:ThumbnailImage");
        cmdparams.add("-preview:PreviewImage");
        // dont' do _JpgFromRaw.jpg as all RAWs have either a thumb or a preview and a _JpgFromRaw.jpg is again quite big
        /*if ("RAW".equals(type)) {
            cmdparams.add("-preview:PreviewImage");
        }*/

        if (isWindows) {
            cmdparams.add(file.getPath().replace("\\", "/"));
        } else {
            cmdparams.add(file.getPath());
        }

        try {
            String cmdResult = CommandRunner.runCommand(cmdparams);
            //logger.info("cmd result from export previews for single RAW" + cmdResult);
        } catch (IOException | InterruptedException ex) {
            logger.debug("Error executing command to export previews for one RAW");
            exportResult = (" " + ResourceBundle.getBundle("translations/program_strings").getString("ept.exporterror"));
        }
        return exportResult;
    }

    /*
     * Create the icon
     */
    public static ImageIcon createIcon(File file) {
        ImageIcon icon = null;
        int[] basicdata = {0, 0, 0};
        boolean bde = false;
        String thumbfilename = "";
        File thumbfile = null;
        String filename = "";
        BufferedImage img = null;
        BufferedImage resizedImg = null;

        filename = file.getName().replace("\\", "/");
        logger.debug("Now working on image: " +filename);
        String filenameExt = Utils.getFileExtension(filename);
        try {
            try {
                // We use exiftool to get width, height and orientation from the original image
                // (as it is not always available in the thumbnail or preview)
                basicdata = ImageFunctions.getbasicImageData(file);
                logger.debug("Width {} Height {} Orientation {}", String.valueOf(basicdata[0]), String.valueOf(basicdata[1]), String.valueOf(basicdata[2]));
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                bde = true;

            }
            logger.trace("after getbasicdata");
            if ((bde) || (basicdata[2]== 999)) {
                // We had some error. Mostly this is the orientation
                basicdata[2]= 1;
            }
            // Check whether we have a thumbnail
            thumbfilename = filename.substring(0, filename.lastIndexOf('.')) + "_ThumbnailImage.jpg";
            thumbfile = new File (MyVariables.gettmpWorkFolder() + File.separator + thumbfilename);
            if (thumbfile.exists()) {
                logger.debug("precreated thumbnail found: {}", thumbfile.toString());
                img = ImageIO.read(new File(thumbfile.getPath().replace("\\", "/")));
            } else {
                logger.debug("precreated thumbnail NOT found: {}", thumbfile.toString());
                img = ImageIO.read(new File(file.getPath().replace("\\", "/")));
            }
            if (basicdata[0] > 160) {
                resizedImg = ImageFunctions.scaleImageToContainer(img, 160, 160);
                logger.trace("after scaleImageToContainer");
            } else {
                // In some circumstances we even have images < 160 width
                resizedImg = img;
            }
            if ( basicdata[2] > 1 ) { //We use 999 if we can' t find an orientation
                resizedImg = ImageFunctions.rotate(resizedImg, basicdata[2]);
            }


            logger.trace("after rotate");

            icon = new ImageIcon(resizedImg);
            return icon;
        } catch (IIOException iex) {
            icon = null;
        } catch (IOException ex) {
            logger.error("Error loading image", ex);
            icon = null;
        }
        return icon;
    }

    /*
    / On Mac we let sips do the conversion of tif and heic images to previews
    / like "sips -s format JPEG -Z 160 test.heic --out test.jpg"
     */
    public static String sipsConvertToJPG(File file, String size) {
        //ImageIcon icon = null;
        //Runtime runtime = Runtime.getRuntime();
        List<String> cmdparams = new ArrayList<String>();
        String exportResult = "Success";

        cmdparams.add("/usr/bin/sips");
        cmdparams.add("-s");
        cmdparams.add("format");
        cmdparams.add("jpeg");
        if (size.toLowerCase().equals("thumb")) {
            cmdparams.add("-Z");
            cmdparams.add("160");
        }
        // Get the file
        cmdparams.add(file.getPath().replace(" ", "\\ "));
        //cmdparams.add("\"" + file.getPath() + "\"");
        cmdparams.add("--out");

        // Get the temporary directory
        String tempWorkDir = MyVariables.gettmpWorkFolder();
        // Get the file name without extension
        String fileName = file.getName();
        int pos = fileName.lastIndexOf(".");
        if (pos > 0 && pos < (fileName.length() - 1)) { // If '.' is not the first or last character.
            fileName = fileName.substring(0, pos);
        }
        cmdparams.add(tempWorkDir + File.separator + fileName + ".jpg");
        logger.info("final sips command: " + cmdparams.toString());

        try {
            String cmdResult = CommandRunner.runCommand(cmdparams);
            logger.trace("cmd result from export previews for single RAW" + cmdResult);
        } catch (IOException | InterruptedException ex) {
            logger.debug("Error executing sipd command to convert to jpg");
        }
        return exportResult;
    }


    /**
     * Resizes an image using a Graphics2D object backed by a BufferedImage.
     * @param src - source image to scale
     * @param w - desired width
     * @param h - desired height
     * @return - the new resized image
     */
    public static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return resizedImg;
    }

    /**
     * Scale an image.
     * @param img the image to be scaled.
     * @param conWidth the maximum width after scaling.
     * @param conHeight the maximum height after scaling.
     * @return the scaled image.
     */
    public static BufferedImage scaleImageToContainer(BufferedImage img, int conWidth, int conHeight) {
        if (img == null) return null;

        // Original image size:
        int width = img.getWidth();
        int height = img.getHeight();

        // If the image is already the right size then there is nothing to do.
        if ((width == conWidth && height <= conHeight) ||
                (height == conHeight && width <= conWidth)) {
            return img;
        }

        // Scaled image size:
        int scaledWidth = conWidth;
        int scaledHeight = conHeight;

        float cAspect = ((float) conWidth) / conHeight;
        float fileAspect = ((float) width) / height;

        if (fileAspect >= cAspect) {
            scaledHeight = (int) (scaledWidth / fileAspect);
        }
        else {
            scaledWidth = (int) (scaledHeight * fileAspect);
        }

        // Prevent scaling to 0 size.
        if (scaledWidth <= 0 || scaledHeight <= 0) {
            scaledWidth = 1;
            scaledHeight = 1;
        }

        // Buffered image for drawing scaled image:
        int type = (img.getTransparency() == Transparency.OPAQUE) ?
                BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage scaledImg = new BufferedImage(scaledWidth, scaledHeight, type);
        Graphics2D g2 = scaledImg.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Draw the scaled image.
        g2.drawImage(img, 0, 0, scaledWidth, scaledHeight, null);
        g2.dispose();

        return scaledImg;
    }

    /**
     * Rotate an image.
     * @param image The image to rotate.
     * @param rotation The rotation constant.
     * @return The rotated image.
     */
    public static BufferedImage rotate(BufferedImage image, int rotation) {
        // http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/EXIF.html tag 0x0112	Orientation
        // "exiftool -exif:orientation" gives for example Rotate 90 CW
        // "exiftool -n -exif:orientation" gives for example 6
        // rotation values:
        // 1 = Horizontal (normal)
        // 2 = Mirror horizontal
        // 3 = Rotate 180
        // 4 = Mirror vertical
        // 5 = Mirror horizontal and rotate 270 CW
        // 6 = Rotate 90 CW
        // 7 = Mirror horizontal and rotate 90 CW
        // 8 = Rotate 270 CW

        AffineTransform tx = null;
        AffineTransformOp op = null;
        if (image == null) return null;

        switch (rotation) {
            default:
            case 1:
                // No rotation
                break;

            case 2:
                // Mirror horizontal
                tx = AffineTransform.getScaleInstance(-1, 1);
                tx.translate(-image.getWidth(null), 0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                break;

            case 3:
                // Rotate 180
                // Relocate the center of the image to the origin.
                // Rotate about the origin.  Then move image back.
                // (This avoids black bars on rotated images.)
                tx = new AffineTransform();
                tx.translate(image.getWidth() / 2.0, image.getHeight() / 2.0);
                tx.rotate(Math.toRadians(180));
                tx.translate( - image.getWidth() / 2.0, - image.getHeight() / 2.0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                break;

            case 4:
                // Mirror vertical
                tx = AffineTransform.getScaleInstance(1, -1);
                tx.translate(0, -image.getHeight(null));
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                break;

            case 5:
                // Mirror horizontal and rotate 270 CW
                tx = AffineTransform.getScaleInstance(-1, 1);
                tx.translate(-image.getWidth(null), 0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                // Fall thru to case 8.

            case 8:
                // Rotate 270 CW
                tx = new AffineTransform();
                tx.translate(image.getHeight() / 2.0, image.getWidth() / 2.0);
                tx.rotate(Math.toRadians(270));
                tx.translate( - image.getWidth() / 2.0, - image.getHeight() / 2.0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                break;

            case 7:
                // Mirror horizontal and rotate 90 CW
                tx = AffineTransform.getScaleInstance(-1, 1);
                tx.translate(-image.getWidth(null), 0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                // Fall through to case 6.

            case 6:
                // Rotate 90 CW
                tx = new AffineTransform();
                tx.translate(image.getHeight() / 2.0, image.getWidth() / 2.0);
                tx.rotate(Math.toRadians(90));
                tx.translate( - image.getWidth() / 2.0, - image.getHeight() / 2.0);
                op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                image = op.filter(image, null);
                break;
        }
        return image;
    }

    /**
     * Adjust an image aspect ratio depending on the image rotation.
     * @param oldAspectRatio The original aspect ratio.
     * @param rotation The rotation constant.
     * @return The adjusted aspect ratio.
     */
    public static float fixAspectRatio(float oldAspectRatio, int rotation) {
        switch (rotation) {
            default:
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                return oldAspectRatio;

            case 5:
            case 6:
            case 7:
            case 8:
                return 1 / oldAspectRatio;
        }
    }

}
