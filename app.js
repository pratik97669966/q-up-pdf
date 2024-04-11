const express = require('express');
const puppeteer = require('puppeteer');
const fs = require('fs');

const app = express();
app.use(express.json()); // Middleware to parse JSON bodies

let browserInstance;

app.post('/', async (req, res) => {
    if (!browserInstance) {
        browserInstance = await puppeteer.launch({ headless: true });
    }
    
    const requestData = req.body;
    // Extracting all fields from the request body
    const clinicAddress = requestData.clinicAddress ;
    const clinicContact = requestData.clinicContact;
    const clinicName = requestData.clinicName;
    const clinicalFindings = requestData.clinicalFindings;
    const diagnoses = requestData.diagnoses;
    const doctorName = requestData.doctorName;
    const instructions = requestData.instructions;
    const investigations = requestData.investigations;
    const languageId = requestData.languageId;
    const medicineDTOS = requestData.medicineDTOS;
    const symptoms = requestData.symptoms;
    const patientBookingRequestId = requestData.patientBookingRequestId;
    const patientName = requestData.patientName;
    const prescriptionId = requestData.prescriptionId;
    const entityId = requestData.entityId;
    const privateInstruction = requestData.privateInstruction;
    const followupDate = requestData.followupDate;
    const followupNote = requestData.followupNote;
    const referredDoctorId = requestData.referredDoctorId;
    const referredDoctorName = requestData.referredDoctorName;
    const doctorDegress = requestData.doctorDegress;
    const patientMobileNumber = requestData.patientMobileNumber;
    const prescriptionDate = requestData.prescriptionDate;
    const patientAgeGender = requestData.patientAgeGender;


    const htmlContent = `<!DOCTYPE html>
    <html lang="en">
    
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Q UP Bill</title>
        <style>
            @import url(https://fonts.googleapis.com/css?family=Roboto:100,300,400,900,700,500,300,100);
    
            * {
                margin: 0;
                box-sizing: border-box;
            }
    
            body {
                background: #E0E0E0;
                font-family: 'Roboto', sans-serif;
                background-image: url('');
                background-repeat: repeat-y;
                background-size: 100%;
            }
    
            ::selection {
                background: #f31544;
                color: #FFF;
            }
    
            ::moz-selection {
                background: #f31544;
                color: #FFF;
            }
    
            h1 {
                font-size: 1.5em;
                color: #222;
            }
    
            h2 {
                font-size: .9em;
            }
    
            h3 {
                font-size: 1.2em;
                font-weight: 300;
                line-height: 2em;
            }
    
            p {
                font-size: .7em;
                color: #666;
                line-height: 1.2em;
            }
    
            #invoiceholder {
                width: 100%;
                height: 100%;
                padding-top: 50px;
            }
    
            #headerimage {
                z-index: -1;
                position: relative;
                overflow: hidden;
                background-attachment: fixed;
                background-size: 1920px 80%;
                background-position: 50% -90%;
            }
    
            #invoice {
                position: relative;
                margin: 0 auto;
                background: #FFF;
            }
    
            [id*='invoice-'] {
                padding: 30px;
            }
    
            [id*='bottomline-'] {
                border-bottom: 1px solid #EEE;
                padding: 30px;
            }
    
            #invoice-bot {
                min-height: 250px;
            }
    
            .logo {
                float: left;
                height: 60px;
                width: 60px;
                background-size: 60px 60px;
            }
    
            .clientlogo {
                float: left;
                height: 60px;
                width: 60px;
                background-size: 60px 60px;
                border-radius: 50px;
            }
    
            .info {
                display: block;
                float: left;
                margin-left: 20px;
            }
    
            .title {
                float: right;
            }
    
            .title p {
                text-align: right;
            }
    
            #project {
                margin-left: 52%;
            }
    
            table {
                width: 100%;
                border-collapse: collapse;
            }
    
            td {
                padding: 5px 0 5px 15px;
                border: 1px solid #EEE
            }
    
            .tabletitle {
                padding: 5px;
                background: #EEE;
            }
    
            .service {
                border: 1px solid #EEE;
            }
    
            .item {
                width: 50%;
            }
    
            .itemtext {
                font-size: .9em;
            }
    
            #legalcopy {
                margin-top: 30px;
            }
    
            form {
                float: right;
                margin-top: 30px;
                text-align: right;
            }
    
            .effect2 {
                position: relative;
            }
    
            .effect2:before,
            .effect2:after {
                z-index: -1;
                position: absolute;
                content: "";
                bottom: 15px;
                left: 10px;
                width: 50%;
                top: 80%;
                max-width: 300px;
                background: #777;
                -webkit-transform: rotate(-3deg);
                -moz-transform: rotate(-3deg);
                -o-transform: rotate(-3deg);
                -ms-transform: rotate(-3deg);
                transform: rotate(-3deg);
            }
    
            .effect2:after {
                -webkit-transform: rotate(3deg);
                -moz-transform: rotate(3deg);
                -o-transform: rotate(3deg);
                -ms-transform: rotate(3deg);
                transform: rotate(3deg);
                right: 10px;
                left: auto;
            }
    
            .legal {
                width: 70%;
            }
        </style>
    </head>
    
    <body>
        <div id="invoiceholder">
            <div>
                <div id="invoice-mid">
                    <div class="info">
                        <h2>${clinicName}</h2>
                        <p>${clinicAddress}<br>
                            Contact :${clinicContact}
                        </p>
                    </div>
                    <div id="project">
                        <h2>${doctorName}</h2>
                        <p>${doctorDegress}
                        </p>
                    </div>
                </div>
            </div>
            <hr style="background-color: rgb(226, 223, 223);">
            <div>
                <div id="invoice-mid">
                    <div class="info">
                        <h2>Name : ${patientName}</h2>
                        <p>Age/Gender : ${patientAgeGender}
                        </p>
                    </div>
                    <div id="project">
                        <h2>Date : ${prescriptionDate}</h2>
                        <p>Mobile No : ${patientMobileNumber}
                        </p>
                    </div>
                </div>
            </div>
            <hr style="background-color: rgb(226, 223, 223);">
            <div id="invoice" class="effect2">
                <div id="invoice-bot">
                    <p><strong>Symptoms : </strong>${symptoms}</p>
                    <br>
    
                    <p><strong>Diagnosis :${diagnoses}</p>
                    <br>
                    <div id="table">
                        <table>
                            <tr class="tabletitle">
                                <td class="sr">
                                    <h2>RX </h2>
                                </td>
                                <td class="item">
                                    <h2>BRANDS </h2>
                                </td>
                                <td class="Hours">
                                    <h2>FREQUENCY </h2>
                                </td>
                                <td class="Rate">
                                    <h2>DURATION </h2>
                                </td>
                                <td class="subtotal">
                                    <h2>QUANTITY </h2>
                                </td>
                            </tr>
                            <tr class="service">
                                <td class="tableitem">
                                    <p class="itemtext">1</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">PARACETAMOL (300MG) +
                                        PARACETAMOL (700MG) +
                                        PARACETAMOL
                                        (250MG/5ML)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Morning + Afternoon +
                                        Night (Before Food)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Days </p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">3</p>
                                </td>
                            </tr>
                            <tr class="service">
                                <td class="tableitem">
                                    <p class="itemtext">1</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">PARACETAMOL (300MG) +
                                        PARACETAMOL (700MG) +
                                        PARACETAMOL
                                        (250MG/5ML)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Morning + Afternoon +
                                        Night (Before Food)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Days </p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">3</p>
                                </td>
                            </tr>
                            <tr class="service">
                                <td class="tableitem">
                                    <p class="itemtext">1</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">PARACETAMOL (300MG) +
                                        PARACETAMOL (700MG) +
                                        PARACETAMOL
                                        (250MG/5ML)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Morning + Afternoon +
                                        Night (Before Food)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Days </p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">3</p>
                                </td>
                            </tr>
                            <tr class="service">
                                <td class="tableitem">
                                    <p class="itemtext">1</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">PARACETAMOL (300MG) +
                                        PARACETAMOL (700MG) +
                                        PARACETAMOL
                                        (250MG/5ML)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Morning + Afternoon +
                                        Night (Before Food)</p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">1 Days </p>
                                </td>
                                <td class="tableitem">
                                    <p class="itemtext">3</p>
                                </td>
                            </tr>
                        </table>
                        <br>
                        <p><strong>Investigations :</strong> ${investigations}</p>
                        <br>
                        <p><strong>General instructions :</strong></p>
                        <p>${instructions}</p>
                        <br>
                        <p><strong>Next Follow-up :</strong> Date:- ${followupDate}</p>
                        <br>
                        <p><strong>Followup Note : </strong> ${followupNote}</p>
                        <br>
                    </div>
                </div>
            </div>
            <div id="invoice-mid">
                <div class="info">
                </div>
                <div class="title">
                    <h2>${doctorName}</h2>
                    <p>${doctorDegress}
                    </p>
                </div>
            </div>
        </div>
    </body>
    
    </html>`;
    const page = await browserInstance.newPage();
    await page.setContent(htmlContent, { waitUntil: 'domcontentloaded' });
    const pdfBuffer = await page.pdf();

    res.setHeader('Content-Type', 'application/pdf');
    res.send(pdfBuffer);
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
    console.log(`Server is running on port ${port}`);
});
