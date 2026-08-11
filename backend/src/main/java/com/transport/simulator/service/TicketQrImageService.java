package com.transport.simulator.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TicketQrImageService {

    public String pngBase64(String qrValue) {
        try {
            var matrix = new QRCodeWriter().encode(
                    qrValue,
                    BarcodeFormat.QR_CODE,
                    420,
                    420,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 3
                    )
            );
            var output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Ticket QR image could not be generated", exception);
        }
    }
}
