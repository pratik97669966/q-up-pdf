const express = require('express');
const PDFDocument = require('pdfkit');
const bodyParser = require('body-parser'); // Import body-parser module
const { detect } = require('langdetect');

const app = express();

// Map Indian languages to Google Noto font file names
const languageToFont = {
    'en': 'english.ttf',
    'hi': 'hindi.ttf',   // Hindi (Devanagari script)
    'te': 'telugu.ttf',       // Telugu
    'mr': 'marathi.ttf',   // Marathi (Devanagari script)
    'ta': 'tamil.ttf',        // Tamil
    'gu': 'gujarati.ttf',     // Gujarati
    'kn': 'kannada.ttf',      // Kannada
    'ml': 'malayalam.ttf',    // Malayalam
    // Add more Indian languages as needed
};

// Use body-parser middleware to parse JSON data
app.use(bodyParser.json());

// Function to detect language and select appropriate font
function selectFont(text) {
    let lang = 'en'; // Default to English if no language detected
    try {
        const detectionResult = detect(text);
        if (detectionResult.length > 0) {
            lang = detectionResult[0].lang;
            console.log('Detected language:', lang); // Log detected language
        }
    } catch (error) {
        console.error('Error detecting language:', error);
    }
    return languageToFont[lang] || 'english.ttf'; // Default font if language not found
}

// API endpoint to generate and return PDF
app.post('/generate-pdf', (req, res) => {
    // Extract text from request body
    const { text } = req.body;

    // Create a new PDF document
    const doc = new PDFDocument();

    // Select appropriate font based on language
    const fontFileName = selectFont(text);
    const fontPath = `./noto-fonts/${fontFileName}`;

    // Set font and render text
    doc.font(fontPath).fontSize(24).text(text, 100, 100);

    // Pipe the PDF document to the response
    res.setHeader('Content-Type', 'application/pdf');
    doc.pipe(res);
    doc.end();
});

// Start the server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
