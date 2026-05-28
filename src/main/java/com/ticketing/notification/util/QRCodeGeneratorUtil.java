package com.ticketing.notification.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import com.ticketing.common.util.BusinessConstants;

/**
 * Utility class for generating Base64-encoded QR codes using Google ZXing.
 * Fix 10.2 — called ONLY from the ticket.generation.queue consumer, NEVER from the HTTP thread.
 */
public final class QRCodeGeneratorUtil {

    private static final Logger logger = LoggerFactory.getLogger(QRCodeGeneratorUtil.class);

    private QRCodeGeneratorUtil() {
        // utility class — no instantiation
    }

    /**
     * Generates a QR code image and returns it as a Base64-encoded PNG string.
     *
     * @param content the text to encode (JSON: ticketId, bookingId, eventId, etc.)
     * @return Base64 PNG string suitable for storing in tickets.qr_code and embedding in HTML emails
     * @throws IllegalStateException if ZXing encoding fails
     */
    public static String generate(String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, BusinessConstants.QR_CODE_MARGIN);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE,
                    BusinessConstants.QR_CODE_SIZE, BusinessConstants.QR_CODE_SIZE, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (WriterException | java.io.IOException e) {
            logger.error("Failed to generate QR code for content='{}': {}", content, e.getMessage());
            throw new IllegalStateException("QR code generation failed", e);
        }
    }
}
