package com.qup.microservices.masterdata.cmsmaster.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.DottedLineSeparator;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.itextpdf.text.pdf.draw.VerticalPositionMark;
import com.qup.microservices.booking.patient.model.CustomPatientBookingRequest;
import com.qup.microservices.booking.patient.model.PatientBookingRequest;
import com.qup.microservices.booking.patient.model.embedded.VitalBean;
import com.qup.microservices.common.utils.CommonConstants;
import com.qup.microservices.masterdata.cms_billing.model.Entity;
import com.qup.microservices.masterdata.cms_billing.model.EntityLogo;
import com.qup.microservices.masterdata.cms_billing.repository.EntityLogoRepository;
import com.qup.microservices.masterdata.cms_billing.repository.EntityRepository;
import com.qup.microservices.masterdata.cms_billing.service.S3BillReceiptStorageService;
import com.qup.microservices.masterdata.cmsmaster.dto.input.*;
import com.qup.microservices.masterdata.cmsmaster.dto.output.MedicalRecordDocumentTypeResource;
import com.qup.microservices.masterdata.cmsmaster.dto.output.RecentPrecriptionDoctor;
import com.qup.microservices.masterdata.cmsmaster.dto.output.UnregisteredDoctorResource;
import com.qup.microservices.masterdata.cmsmaster.enums.UploadedBy;
import com.qup.microservices.masterdata.cmsmaster.exception.PatientPrescriptionPhotoProcessingException;
import com.qup.microservices.masterdata.cmsmaster.exception.PrescriptionNotFoundException;
import com.qup.microservices.masterdata.cmsmaster.model.*;
import com.qup.microservices.masterdata.cmsmaster.repository.*;
import com.qup.microservices.masterdata.doctor.model.Doctor;
import com.qup.microservices.masterdata.doctor.model.EducationDegree;
import com.qup.microservices.masterdata.doctor.repository.DoctorRepository;
import com.qup.microservices.masterdata.medicalInfo.model.Gynec;
import com.qup.microservices.masterdata.medicalInfo.model.Surgery;
import com.qup.microservices.masterdata.medicalInfo.model.embeded.BreastCancer;
import com.qup.microservices.masterdata.medicalInfo.model.embeded.ChildWithMentalOrGeneticDisorder;
import com.qup.microservices.masterdata.medicalInfo.model.embeded.MenopausheAge;
import com.qup.microservices.masterdata.medicalInfo.repository.GynecRepository;
import com.qup.microservices.notification.communication.dto.input.FirebaseNotificationType;
import com.qup.microservices.patient.dto.output.PatientFamilyMemberResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.process.Pipe;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.qup.microservices.common.utils.CommonConstants.INDIA_TIME_ZONE;

@SuppressWarnings("unused")
@Service
@Slf4j
public class PatientPrescriptionServiceImpl implements PatientPrescriptionService {

    //public static final String TELUGU_ID = "605436d5e1a625000c58a458"; //dev
    public static final String TELUGU_ID = "605494aaeffab3000c74de50"; //prod
    public static final String GUJARATI_ID = "60b2254031f0b5000c128767"; //dev
    @Autowired
    private PatientPrescriptionRepository patientPrescriptionRepository;
    @Autowired
    private S3BillReceiptStorageService s3BillReceiptStorageService;
    @Autowired
    private GynecRepository gynecRepository;

    @Value("${qup.formulationImage.thumbNailSizeInPixels}")
    private int thumbnailSize;

    @Value("${qup.imagemagick.path}")
    private String imageMagickExecutablePath;

    @Value("${aws.s3.bucketNamePatientPrescription}")
    private String s3BucketNamePatientPrescription;

    @Value("classpath:/font/NotoSans-Light.ttf")
    private String notoSans;

    //TODO : need to configure , It holds QUP Patient app download link
    /*@Value("${qup.notification.addPatient.appLink}")
    private String qupAppDownloadLink;
*/
    @Autowired
    private AmazonS3 amazonS3Client;

    @Autowired
    private UnregisteredDoctorRepository unregisteredDoctorRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PrescriptionInfoReposirtory prescriptionInfoReposirtory;

    @Autowired
    private CmsRecentSuggestionMatserRepository cmsRecentSuggestionMatserRepository;

    @Autowired
    private BrandMasterRepository brandMasterRepository;

    @Autowired
    private GenericMasterRepository genericMasterRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private FormulationMasterRepository formulationMasterRepository;

    @Autowired
    private EntityLogoRepository entityLogoRepository;

    @Autowired
    private MedicalRecordDocumentTypeRepository medicalRecordDocumentTypeRepository;

    @Autowired
    private PrescriptionSettingsRepository prescriptionSettingsRepository;

    @Autowired
    private PrescriptionPrintSettingsRepository prescriptionPrintSettingsRepository;

    @Value("${aws.s3.entity.logo.bucketName}")
    private String s3BucketNameEntity;

    @Autowired
    private PatientBookingRequestRepository patientBookingRequestRepository;

    @Autowired
    private CustomPatientBookingRequestRepository customPatientBookingRequestRepository;

    @Autowired
    private IndividualClinicAppMappingRepository individualClinicAppMappingRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CmsSpecialityRelevantMasterRepository cmsSpecialityRelevantMasterRepository;

    @Autowired
    private CmsTranslationRepository cmsTranslationRepository;

    @Autowired
    private PrivateInstructionRepository privateInstructionRepository;

    private static final String NOTIFICATION_SERVICE_PRESCRIPTION_INFORMATION = "http://notification-webservice/notification-service/communication/notification/prescription/alert";
    private static final String NOTIFICATION_SERVICE_ALERT = "http://notification-webservice/notification-service/communication/notification/alert";


    private static final String DATE_FORMAT_FOR_RECEIPT = "dd-MMM-yyyy hh:mm a";
    private static final String THUMBNAIL_OUTPUT_FORMAT = "png";
    private static final String TEMPORARY_FILE_EXTENSION = ".png";
    private static final String STANDARD_IN_CHANNEL = "-";
    private static final String THUMBNAIL_GRAVITY_CENTER = "center";

    private static final String GET_PATIENT_FAMILY_MEMBER = "http://patient-webservice/patient-service/patient/family/by-id/";

    private static final String QUP_APP_DOWNLOAD_LINK =  "https://qup1.in/qupapp";


    @Override
    public PatientPrescription createPatientPrescription(String id, String pdfUrl) {
        PatientPrescription byId = patientPrescriptionRepository.findById(id);
        byId.setS3PrescriptionPath(pdfUrl);
        return patientPrescriptionRepository.save(byId);
    }

    @Override
    public PatientPrescription getPrescription(String prescriptionId) throws PrescriptionNotFoundException {
        PatientPrescription byId = patientPrescriptionRepository.findById(prescriptionId);
        if (byId != null) {
            return byId;
        } else {
            throw new PrescriptionNotFoundException(String.format("No Prescription found associated with the id [ %s ]", prescriptionId));
        }
    }

    @Override
    public Page<PatientPrescription> getPatientPrescriptionByNumber(GetPatientPrescriptions getPatientPrescriptions, int page, int size) {
        Pageable pageable = new PageRequest(page, size, Sort.Direction.DESC, CommonConstants.DOCUMENT_CREATED_AT_FIELD_NAME);
        return patientPrescriptionRepository.findByFamilyMemberIdAndDoctorIdAndEntityIdAndDocumentType(getPatientPrescriptions.getFamilyMemberId(),
                getPatientPrescriptions.getDoctorId(), getPatientPrescriptions.getEntityId(), getPatientPrescriptions.getDocumentType(), pageable);
    }

    @Override
    public Page<PatientPrescription> getPatientPrescriptionByNumberForPatient(GetPatientPrescriptions getPatientPrescriptions, int page, int size) {
        Pageable pageable = new PageRequest(page, size, Sort.Direction.DESC, CommonConstants.DOCUMENT_CREATED_AT_FIELD_NAME);
        return patientPrescriptionRepository.findByFamilyMemberIdAndShareToPatient(getPatientPrescriptions.getFamilyMemberId(),
                true, pageable);
    }

    @Override
    public PatientPrescription addPatientPrescription(PatientPrescriptionDTO patientPrescriptionDTO) {
        PatientPrescription patientPrescription = new PatientPrescription();
        BeanUtils.copyProperties(patientPrescriptionDTO, patientPrescription);
        return patientPrescriptionRepository.save(patientPrescription);
    }

    private static BaseFont getBaseFont(String languageId) throws DocumentException, IOException {

        switch (languageId) {

            case TELUGU_ID:
                return BaseFont.createFont("/assets/NotoSansTelugu-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);


            case GUJARATI_ID:
                return BaseFont.createFont("/assets/NotoSansGujarati-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);


            default:
                return BaseFont.createFont("/assets/NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);


        }

    }

    private void saveToRelevantData(PatientPrescriptionDataDTO patientPrescriptionDataDTO, String doctorId) {
        log.info("###### Saving Speciality relevant Data ######");
        Doctor doctor = doctorRepository.findOne(doctorId);
        doctor.getSpecialitySet().forEach(s -> saveSpecialityRelevantData(s.getSpecialityId(), patientPrescriptionDataDTO));
    }

    private void saveSpecialityRelevantData(String specialityId, PatientPrescriptionDataDTO patientPrescriptionDataDTO) {
        log.info("###### Saving Speciality relevant Data for speciality id " + specialityId + " ######");
        Optional<CmsSpecialityRelevantMaster> cmsSpecialityRelevantMaster = cmsSpecialityRelevantMasterRepository.findBySpecialityId(specialityId);
        List<Brand> brandSet = new ArrayList<>();
        List<Generic> genericSet = new ArrayList<>();
        if (cmsSpecialityRelevantMaster.isPresent()) {
            CmsSpecialityRelevantMaster specialityRelevantMaster = cmsSpecialityRelevantMaster.get();
            specialityRelevantMaster.getMedicineDTOS().addAll(new ArrayList<>(patientPrescriptionDataDTO.getMedicineDTOS()));
            specialityRelevantMaster.getSymptoms().addAll(getSymptomsToBeAddedInTheSpecialityRelvant(patientPrescriptionDataDTO, specialityRelevantMaster));
            specialityRelevantMaster.getDiagnoses().addAll(getDiagnosisToBeAddedInTheSpecialityRelvant(patientPrescriptionDataDTO, specialityRelevantMaster));
            specialityRelevantMaster.getClinicalFindings().addAll(getCfToBeAddedInTheSpecialityRelvant(patientPrescriptionDataDTO, specialityRelevantMaster));
            if (specialityRelevantMaster.getInstructions() != null) {
                specialityRelevantMaster.getInstructions().addAll(getInstructionsToBeAddedInTheSpecialityRelvant(patientPrescriptionDataDTO, specialityRelevantMaster));
            } else {
                specialityRelevantMaster.setInstructions(new ArrayList<>(patientPrescriptionDataDTO.getInstructions()));
            }
            if (specialityRelevantMaster.getInvestigations() != null) {
                specialityRelevantMaster.getInvestigations().addAll(new ArrayList<>(patientPrescriptionDataDTO.getInvestigations()));
            } else {
                specialityRelevantMaster.setInvestigations(new ArrayList<>(patientPrescriptionDataDTO.getInvestigations()));
            }

            if (patientPrescriptionDataDTO.getMedicineDTOS() != null && !patientPrescriptionDataDTO.getMedicineDTOS().isEmpty()) {
                prepareRecentBrandOrGeneric(patientPrescriptionDataDTO, brandSet, genericSet);
                if (specialityRelevantMaster.getBrands() != null) {
                    specialityRelevantMaster.getBrands().addAll(brandSet.stream().filter(b -> b.getDoctorId() == null).collect(Collectors.toSet()));
                } else {
                    specialityRelevantMaster.setBrands(brandSet);
                }

                if (specialityRelevantMaster.getGenerics() != null) {
                    specialityRelevantMaster.getGenerics().addAll(genericSet.stream().filter(g -> g.getDoctorId() == null).collect(Collectors.toSet()));
                } else {
                    specialityRelevantMaster.setGenerics(genericSet);
                }
            }
            cmsSpecialityRelevantMasterRepository.save(specialityRelevantMaster);
        } else {
            CmsSpecialityRelevantMaster newCmsMaster = new CmsSpecialityRelevantMaster();
            newCmsMaster.setSymptoms(new ArrayList<>(patientPrescriptionDataDTO.getSymptoms()));
            newCmsMaster.setDiagnoses(new ArrayList<>(patientPrescriptionDataDTO.getDiagnoses()));
            newCmsMaster.setClinicalFindings(new ArrayList<>(patientPrescriptionDataDTO.getClinicalFindings()));
            newCmsMaster.setMedicineDTOS(new ArrayList<>(patientPrescriptionDataDTO.getMedicineDTOS()));
            newCmsMaster.setInvestigations(new ArrayList<>(patientPrescriptionDataDTO.getInvestigations()));
            newCmsMaster.setInstructions(new ArrayList<>(patientPrescriptionDataDTO.getInstructions()));
            newCmsMaster.setSpecialityId(specialityId);
            if (patientPrescriptionDataDTO.getMedicineDTOS() != null && !patientPrescriptionDataDTO.getMedicineDTOS().isEmpty()) {
                prepareRecentBrandOrGeneric(patientPrescriptionDataDTO, brandSet, genericSet);
                newCmsMaster.setBrands(brandSet.stream().filter(b -> b.getDoctorId() == null).collect(Collectors.toList()));
                newCmsMaster.setGenerics(genericSet.stream().filter(g -> g.getDoctorId() == null).collect(Collectors.toList()));
            } else {
                newCmsMaster.setBrands(brandSet);
                newCmsMaster.setGenerics(genericSet);
            }
            cmsSpecialityRelevantMasterRepository.save(newCmsMaster);
        }

    }

    private List<Instruction> getInstructionsToBeAddedInTheSpecialityRelvant(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsSpecialityRelevantMaster specialityRelevantMaster) {
        List<Instruction> instructionsToReturn = specialityRelevantMaster.getInstructions();
        List<Instruction> instructionsToTraverse = patientPrescriptionDataDTO.getInstructions().stream().filter(s -> s.getDoctorId() == null).collect(Collectors.toList());
        for (Instruction instruction : instructionsToTraverse) {
            List<Instruction> insToBeReplaced = specialityRelevantMaster.getInstructions().stream().filter(ins1 -> ins1.getId().equals(instruction.getId())).collect(Collectors.toList());
            if (!insToBeReplaced.isEmpty()) {
                instructionsToReturn.removeAll(insToBeReplaced);
                instructionsToReturn.add(instruction);
            } else {
                instructionsToReturn.add(instruction);
            }
        }
        return instructionsToReturn;
    }

    private List<ClinicalFinding> getCfToBeAddedInTheSpecialityRelvant(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsSpecialityRelevantMaster specialityRelevantMaster) {
        List<ClinicalFinding> cfsToReturn = specialityRelevantMaster.getClinicalFindings();
        List<ClinicalFinding> cfsToTraverse = patientPrescriptionDataDTO.getClinicalFindings().stream().filter(s -> s.getDoctorId() == null).collect(Collectors.toList());
        for (ClinicalFinding clinicalFinding : cfsToTraverse) {
            List<ClinicalFinding> cfToReplace = specialityRelevantMaster.getClinicalFindings().stream().filter(cf1 -> cf1.getId().equals(clinicalFinding.getId())).collect(Collectors.toList());
            if (!cfToReplace.isEmpty()) {
                cfsToReturn.removeAll(cfToReplace);
                cfsToReturn.add(clinicalFinding);
            } else {
                cfsToReturn.add(clinicalFinding);
            }
        }
        return cfsToReturn;
    }

    private List<Diagnosis> getDiagnosisToBeAddedInTheSpecialityRelvant(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsSpecialityRelevantMaster specialityRelevantMaster) {
        List<Diagnosis> diagnosisToReturn = specialityRelevantMaster.getDiagnoses();
        List<Diagnosis> diagnosisToTraverse = patientPrescriptionDataDTO.getDiagnoses().stream().filter(s -> s.getDoctorId() == null).collect(Collectors.toList());
        for (Diagnosis diagnosis : diagnosisToTraverse) {
            List<Diagnosis> diagnosisToReplace = specialityRelevantMaster.getDiagnoses().stream().filter(diagnosis1 -> diagnosis1.getId().equals(diagnosis.getId())).collect(Collectors.toList());
            if (!diagnosisToReplace.isEmpty()) {
                diagnosisToReturn.removeAll(diagnosisToReplace);
                diagnosisToReturn.add(diagnosis);
            } else {
                diagnosisToReturn.add(diagnosis);
            }
        }
        return diagnosisToReturn;
    }

    private List<Symptom> getSymptomsToBeAddedInTheSpecialityRelvant(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsSpecialityRelevantMaster specialityRelevantMaster) {
        List<Symptom> symptomsToReturn = specialityRelevantMaster.getSymptoms();
        List<Symptom> symptomsToTraverse = patientPrescriptionDataDTO.getSymptoms().stream().filter(s -> s.getDoctorId() == null).collect(Collectors.toList());
        for (Symptom symptom : symptomsToTraverse) {
            List<Symptom> symptomToReplace = specialityRelevantMaster.getSymptoms().stream().filter(symptom1 -> symptom1.getId().equals(symptom.getId())).collect(Collectors.toList());
            if (!symptomToReplace.isEmpty()) {
                symptomsToReturn.removeAll(symptomToReplace);
                symptomsToReturn.add(symptom);
            } else {
                symptomsToReturn.add(symptom);
            }
        }
        return symptomsToReturn;
    }


    private void saveToRecentSuggestion(PatientPrescriptionDataDTO patientPrescriptionDataDTO, String doctorId) {
        Optional<CmsRecentSuggestionMatser> cmsRecentSuggestionMatser = cmsRecentSuggestionMatserRepository.findByDoctorId(doctorId);
        List<Brand> brandSet = new ArrayList<>();
        List<Generic> genericSet = new ArrayList<>();
        if (cmsRecentSuggestionMatser.isPresent()) {
            CmsRecentSuggestionMatser updateCmsMaster = cmsRecentSuggestionMatser.get();
            updateCmsMaster.getSymptoms().addAll(getSymptomsToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));
            updateCmsMaster.getDiagnoses().addAll(getDiagnosisToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));
            updateCmsMaster.getClinicalFindings().addAll(getClinicalFindingsToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));
            updateCmsMaster.getMedicineDTOS().addAll(getMedicineDTOsToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));

            if (updateCmsMaster.getInstructions() != null) {
                updateCmsMaster.getInstructions().addAll(getInstructionsToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));
            } else {
                updateCmsMaster.setInstructions(new ArrayList<>(patientPrescriptionDataDTO.getInstructions()));
            }
            if (updateCmsMaster.getInvestigations() != null) {
                updateCmsMaster.getInvestigations().addAll(getInvestigationToBeAddedInTheSuggestions(patientPrescriptionDataDTO, updateCmsMaster));
            } else {
                updateCmsMaster.setInvestigations(new ArrayList<>(patientPrescriptionDataDTO.getInvestigations()));
            }
            if (patientPrescriptionDataDTO.getMedicineDTOS() != null && !patientPrescriptionDataDTO.getMedicineDTOS().isEmpty()) {
                prepareRecentBrandOrGeneric(patientPrescriptionDataDTO, brandSet, genericSet);
                if (updateCmsMaster.getBrands() != null) {
                    updateCmsMaster.getBrands().addAll(brandSet);
                } else {
                    updateCmsMaster.setBrands(brandSet);
                }

                if (updateCmsMaster.getGenerics() != null) {
                    updateCmsMaster.getGenerics().addAll(genericSet);
                } else {
                    updateCmsMaster.setGenerics(genericSet);
                }
            }
            cmsRecentSuggestionMatserRepository.save(updateCmsMaster);
        } else {
            CmsRecentSuggestionMatser newCmsMaster = new CmsRecentSuggestionMatser();
            newCmsMaster.setSymptoms(new ArrayList<>(patientPrescriptionDataDTO.getSymptoms()));
            newCmsMaster.setDiagnoses(new ArrayList<>(patientPrescriptionDataDTO.getDiagnoses()));
            newCmsMaster.setClinicalFindings(new ArrayList<>(patientPrescriptionDataDTO.getClinicalFindings()));
            newCmsMaster.setMedicineDTOS(new ArrayList<>(patientPrescriptionDataDTO.getMedicineDTOS()));
            newCmsMaster.setInvestigations(new ArrayList<>(patientPrescriptionDataDTO.getInvestigations()));
            newCmsMaster.setInstructions(new ArrayList<>(patientPrescriptionDataDTO.getInstructions()));
            newCmsMaster.setDoctorId(doctorId);
            if (patientPrescriptionDataDTO.getMedicineDTOS() != null && !patientPrescriptionDataDTO.getMedicineDTOS().isEmpty()) {
                prepareRecentBrandOrGeneric(patientPrescriptionDataDTO, brandSet, genericSet);
                newCmsMaster.setBrands(brandSet);
                newCmsMaster.setGenerics(genericSet);
            } else {
                newCmsMaster.setBrands(brandSet);
                newCmsMaster.setGenerics(genericSet);
            }
            cmsRecentSuggestionMatserRepository.save(newCmsMaster);
        }
    }

    private List<Investigation> getInvestigationToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<Investigation> investigationToReturn = new ArrayList<>();
        for (Investigation investigation : patientPrescriptionDataDTO.getInvestigations()) {
            List<Investigation> investigationToReplace = cmsRecentSuggestionMatser.getInvestigations().stream().filter(i -> i.getId() != null && i.getId().equals(investigation.getId())).collect(Collectors.toList());
            if (!investigationToReplace.isEmpty()) {
                cmsRecentSuggestionMatser.getInvestigations().removeAll(investigationToReplace);
                investigationToReturn.add(investigation);
            } else {
                investigationToReturn.add(investigation);
            }
        }
        return investigationToReturn;
    }

    private List<MedicineDTO> getMedicineDTOsToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<MedicineDTO> medicineDtoToReturn = new ArrayList<>();
        for (MedicineDTO medicineDTO : patientPrescriptionDataDTO.getMedicineDTOS()) {
            List<MedicineDTO> medicineDTOToReplace = cmsRecentSuggestionMatser.getMedicineDTOS().stream().filter(md -> md.getBrandId() != null && md.getBrandId().equals(medicineDTO.getBrandId()) || md.getGenericId() != null && md.getGenericId().equals(medicineDTO.getGenericId())).collect(Collectors.toList());
            if (!medicineDTOToReplace.isEmpty()) {
                cmsRecentSuggestionMatser.getMedicineDTOS().removeAll(medicineDTOToReplace);
                medicineDtoToReturn.add(medicineDTO);
            } else {
                medicineDtoToReturn.add(medicineDTO);
            }
        }
        return medicineDtoToReturn;
    }

    private List<Symptom> getSymptomsToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<Symptom> symptomsToReturn = new ArrayList<>();
        for (Symptom symptom : patientPrescriptionDataDTO.getSymptoms()) {
            List<Symptom> symptomToReplace = cmsRecentSuggestionMatser.getSymptoms().stream().filter(s -> s.getId() != null && s.getId().equals(symptom.getId())).collect(Collectors.toList());
            if (!symptomToReplace.isEmpty()) {
                cmsRecentSuggestionMatser.getSymptoms().removeAll(symptomToReplace);
                symptomsToReturn.add(symptom);
            } else {
                symptomsToReturn.add(symptom);
            }
        }
        return symptomsToReturn;
    }

    private List<Diagnosis> getDiagnosisToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<Diagnosis> diagnosisToReturn = new ArrayList<>();
        for (Diagnosis diagnosis : patientPrescriptionDataDTO.getDiagnoses()) {
            List<Diagnosis> diagnosisToReplace = cmsRecentSuggestionMatser.getDiagnoses().stream().filter(diagnosis1 -> diagnosis1.getId() != null && diagnosis1.getId().equals(diagnosis.getId())).collect(Collectors.toList());
            if (!diagnosisToReplace.isEmpty()) {
                cmsRecentSuggestionMatser.getDiagnoses().removeAll(diagnosisToReplace);
                diagnosisToReturn.add(diagnosis);
            } else {
                diagnosisToReturn.add(diagnosis);
            }
        }
        return diagnosisToReturn;
    }

    private List<ClinicalFinding> getClinicalFindingsToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<ClinicalFinding> cfsToReturn = new ArrayList<>();
        for (ClinicalFinding clinicalFinding : patientPrescriptionDataDTO.getClinicalFindings()) {
            List<ClinicalFinding> cfToReplace = cmsRecentSuggestionMatser.getClinicalFindings().stream().filter(cf1 -> cf1.getId() != null && cf1.getId().equals(clinicalFinding.getId())).collect(Collectors.toList());
            if (!cfToReplace.isEmpty()) {
                cmsRecentSuggestionMatser.getClinicalFindings().removeAll(cfToReplace);
                cfsToReturn.add(clinicalFinding);
            } else {
                cfsToReturn.add(clinicalFinding);
            }
        }
        return cfsToReturn;
    }

    private List<Instruction> getInstructionsToBeAddedInTheSuggestions(PatientPrescriptionDataDTO patientPrescriptionDataDTO, CmsRecentSuggestionMatser cmsRecentSuggestionMatser) {
        List<Instruction> instructionsToReturn = new ArrayList<>();
        for (Instruction instruction : patientPrescriptionDataDTO.getInstructions()) {
            List<Instruction> insToBeReplaced = cmsRecentSuggestionMatser.getInstructions().stream().filter(ins1 -> ins1.getId() != null && ins1.getId().equals(instruction.getId())).collect(Collectors.toList());
            if (!insToBeReplaced.isEmpty()) {
                cmsRecentSuggestionMatser.getInstructions().removeAll(insToBeReplaced);
                instructionsToReturn.add(instruction);
            } else {
                instructionsToReturn.add(instruction);
            }
        }
        return instructionsToReturn;
    }

    private void prepareRecentBrandOrGeneric(PatientPrescriptionDataDTO patientPrescriptionDataDTO, List<Brand> brandSet, List<Generic> genericSet) {
        patientPrescriptionDataDTO.getMedicineDTOS().forEach(medicineDTO -> {
            if (medicineDTO.getBrandId() != null) {
                Brand brand = brandMasterRepository.findOne(medicineDTO.getBrandId());
                if (brand != null) {
                    Formulation formulation = null;
                    if (brand.getFormulationId() != null)
                        formulation = formulationMasterRepository.findOne(brand.getFormulationId());

                    assert formulation != null;
                    brand.setDispensingUnit(formulation.getDispensingUnit() != null ? formulation.getDispensingUnit().get(0) : "");
                    brandSet.add(brand);
                }
            }

            if (medicineDTO.getGenericId() != null) {
                Generic generic = genericMasterRepository.findOne(medicineDTO.getGenericId());
                if (generic != null)
                    genericSet.add(generic);
            }
        });
    }

    @Override
    public PatientPrescription uploadPatientPrescription(UploadPrescriptionDTO uploadPrescriptionDTO) {
        PatientPrescription patientPrescription = new PatientPrescription();
        BeanUtils.copyProperties(uploadPrescriptionDTO, patientPrescription);
        patientPrescription.setShareToPatient(true);
        patientPrescription.setPrescriptionDate(DateTime.now());

        patientPrescription = patientPrescriptionRepository.save(patientPrescription);
        NotificationPrescriptionDTO notificationPrescriptionDTO = getNotificationPrescriptionDTO(uploadPrescriptionDTO, patientPrescription);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<NotificationPrescriptionDTO> request = new HttpEntity<>(notificationPrescriptionDTO, headers);
            this.restTemplate.postForEntity(NOTIFICATION_SERVICE_ALERT, request, Void.class);
            // this.restTemplate.postForObject(NOTIFICATION_SERVICE_PRESCRIPTION_INFORMATION,notificationPrescriptionDTO,Void.class);
        } catch (Exception exception) {
            log.error("Error sending notification : {}", exception.getMessage());
        }
        return patientPrescription;

    }

    private static NotificationPrescriptionDTO getNotificationPrescriptionDTO(UploadPrescriptionDTO uploadPrescriptionDTO, PatientPrescription patientPrescription) {
        NotificationPrescriptionDTO notificationPrescriptionDTO = new NotificationPrescriptionDTO();
        notificationPrescriptionDTO.setRecordId(patientPrescription.getId());
        notificationPrescriptionDTO.setNotificationType(FirebaseNotificationType.MEDICAL_RECORD);
        notificationPrescriptionDTO.setDoctorName(uploadPrescriptionDTO.getDoctorName());
        notificationPrescriptionDTO.setMobileNumber(uploadPrescriptionDTO.getMobileNumber());
        notificationPrescriptionDTO.setFamilyMemberId(uploadPrescriptionDTO.getFamilyMemberId());
        notificationPrescriptionDTO.setDoctorId(uploadPrescriptionDTO.getDoctorId());
        notificationPrescriptionDTO.setIndiAppId(uploadPrescriptionDTO.getIndiAppId());
        return notificationPrescriptionDTO;
    }


    public PatientFamilyMemberResource getPatientFamilyMember(String familyMemberId) {
        try {

            PatientFamilyMemberResource response = this.restTemplate.getForObject(
                    URI.create(GET_PATIENT_FAMILY_MEMBER + familyMemberId), PatientFamilyMemberResource.class);

            Assert.isTrue(response != null,
                    "Did NOT get 2xx HTTP status code when getting family member data");
            return response;
        } catch (RestClientException restClientException) {
            log.error(String.format(
                    "Rest Client error while trying to get family member id : [ %s ] , %s",
                    familyMemberId, restClientException.getMessage()));
            throw restClientException;
        }
    }


    @Override
    public String uploadPrescriptionImageFileToS3(MultipartFile file, String prescriptionId) {
        try {

            String s3ObjectName = String.format("%s.%s", prescriptionId, THUMBNAIL_OUTPUT_FORMAT);
            // Upload to S3
            this.amazonS3Client.putObject(this.s3BucketNamePatientPrescription + "/pdf", s3ObjectName, file.getInputStream(), null);


            return s3ObjectName;
        } catch (IOException ioException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("IO exception while processing photo: [ %s ]", ioException.getMessage()),
                    ioException);
        } catch (AmazonServiceException amazonServiceException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("Amazon Service exception while processing photo: [ %s ]",
                            amazonServiceException.getMessage()),
                    amazonServiceException);
        } catch (SdkClientException sdkclientException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("AWS SDK exception while processing photo: [ %s ]", sdkclientException.getMessage()),
                    sdkclientException);
        }
    }

    @Override
    public PatientPrescription savePrescriptionWithImage(PatientPrescription patientPrescription) {
        return patientPrescriptionRepository.save(patientPrescription);
    }

    @Override
    public UnregisteredDoctorResource saveUnregisteredDoctor(UnregisteredDoctorDTO unregisteredDoctorDTO) {
        UnRegisteredDoctor unRegisteredDoctor = new UnRegisteredDoctor();
        BeanUtils.copyProperties(unregisteredDoctorDTO, unRegisteredDoctor);
        unRegisteredDoctor = unregisteredDoctorRepository.save(unRegisteredDoctor);
        UnregisteredDoctorResource unregisteredDoctorResource = new UnregisteredDoctorResource();
        BeanUtils.copyProperties(unRegisteredDoctor, unregisteredDoctorResource);
        unregisteredDoctorResource.setUnregisteredDoctorId(unRegisteredDoctor.getId());
        return unregisteredDoctorResource;
    }

    @Override
    public List<RecentPrecriptionDoctor> recentDoctorForPassedMobileNumber(Long patientMobileNumber) {
        List<RecentPrecriptionDoctor> recentPrescriptionDoctors = new ArrayList<>();
        List<PatientPrescription> patientPrescriptionList = patientPrescriptionRepository.findByMobileNumber(patientMobileNumber);
        for (PatientPrescription patientPrescription : patientPrescriptionList) {
            if (patientPrescription.getDoctorId() != null) {
                Doctor doctor = doctorRepository.findOne(patientPrescription.getDoctorId());
                if (doctor != null) {
                    RecentPrecriptionDoctor recentPrecriptionDoctor = new RecentPrecriptionDoctor();
                    recentPrecriptionDoctor.setDoctorId(doctor.getId());
                    recentPrecriptionDoctor.setDoctorName(doctor.getUserInfo().getDoctorFullName());
                    recentPrescriptionDoctors.add(recentPrecriptionDoctor);
                }
            } else {
                UnRegisteredDoctor unRegisteredDoctor = unregisteredDoctorRepository.findOne(patientPrescription.getUnRegisteredDoctorId());
                if (unRegisteredDoctor != null) {
                    RecentPrecriptionDoctor recentPrecriptionDoctor = new RecentPrecriptionDoctor();
                    recentPrecriptionDoctor.setUnregisteredDoctorId(unRegisteredDoctor.getId());
                    recentPrecriptionDoctor.setDoctorName(unRegisteredDoctor.getDoctorName());
                    recentPrescriptionDoctors.add(recentPrecriptionDoctor);
                }
            }
        }

        return recentPrescriptionDoctors;
    }

    @Override
    public PrescriptionInfo createPrescriptionInfo(PrescriptionInfoDTO prescriptionInfoDTO) {
        PrescriptionInfo prescriptionInfo = new PrescriptionInfo();
        BeanUtils.copyProperties(prescriptionInfoDTO, prescriptionInfo);
        return prescriptionInfoReposirtory.save(prescriptionInfo);
    }

    private byte[] getImageLogo(String entityId) {
        EntityLogo entityLogo = entityLogoRepository.findByEntityId(entityId);
        if (entityLogo == null) {
            return null;
        }
        String s3ObjectName = entityLogo.getImagePath();

        try {
            S3Object s3object;

            s3object = this.amazonS3Client.getObject(this.s3BucketNameEntity, s3ObjectName);

            return IOUtils.toByteArray(s3object.getObjectContent());
        } catch (AmazonServiceException amazonServiceException) {
            throw new RuntimeException(
                    String.format("Amazon Service exception while downloading logo [ %s ] pdf: [ %s ]", s3ObjectName,
                            amazonServiceException.getMessage()),
                    amazonServiceException);
        } catch (SdkClientException sdkclientException) {
            throw new RuntimeException(
                    String.format("AWS SDK exception while downloading logo [ %s ] pdf: [ %s ]", s3ObjectName,
                            sdkclientException.getMessage()),
                    sdkclientException);
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("error converting to byte array; [ %s ]",
                            e.getMessage()));
        }
    }

    private static String getFormattedQuantity(String m) {
        if (m.contains(".0")) {
            return String.valueOf((int) Double.parseDouble(m));
        } else {
            return m;
        }

    }

    @Override
    public void savePrescription(PatientPrescriptionDataDTO patientPrescriptionDataDTO) {
        PatientPrescription byId = patientPrescriptionRepository.findById(patientPrescriptionDataDTO.getPrescriptionId());

        if (byId.getMobileNumber() != null)
            patientPrescriptionDataDTO.setMobileNumber(byId.getMobileNumber());
        if (byId.getAge() != null)
            patientPrescriptionDataDTO.setAge(byId.getAge());
        if (byId.getGender() != null)
            patientPrescriptionDataDTO.setGender(byId.getGender());

        patientPrescriptionDataDTO.setFamilyMemberId(byId.getFamilyMemberId());

        Doctor doctor = doctorRepository.findOne(byId.getDoctorId());

        File file = new File("");
        try {
            file = createPdf(patientPrescriptionDataDTO, doctor, byId.getEntityId());
        } catch (Exception e) {
            log.info("Pdf creation failed!! " + e.getMessage());
        }
        //  MultipartFile multipartFile=convertFileToMultipart(file);
        if (file != null && file.exists()) {
            String date = DateTime.now(DateTimeZone.forID(INDIA_TIME_ZONE)).toString(DATE_FORMAT_FOR_RECEIPT);
            String fileNameToSave = patientPrescriptionDataDTO.getPrescriptionId();
            String pdfUrl = s3BillReceiptStorageService.uploadPrescriptionToS3(fileNameToSave, file);

           /* if (byId.getEmail() != null && !byId.getEmail().isEmpty()) {
                try {
                    sendEmailAsync(file, byId.getEmail(), pdfUrl, byId.getPatientName());
                } catch (SendFailedException e) {
                    String errorMessage = "Email is not valid";
                    log.error(errorMessage);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }*/

            FileUtils.deleteQuietly(file);


            PrescriptionSettings prescriptionSettings = prescriptionSettingsRepository.findByDoctorId(doctor.getId());
            if (prescriptionSettings.getDiagnosis()) {
                byId.setDiagnoses(patientPrescriptionDataDTO.getDiagnoses());
            }
            if (prescriptionSettings.getSymptoms()) {
                byId.setSymptoms(patientPrescriptionDataDTO.getSymptoms());
            }
            if (prescriptionSettings.getClinicalFinding()) {
                byId.setClinicalFindings(patientPrescriptionDataDTO.getClinicalFindings());
            }
            byId.setDoctorName(patientPrescriptionDataDTO.getDoctorName());
            byId.setMedicineDTOS(patientPrescriptionDataDTO.getMedicineDTOS());
            byId.setInvestigations(patientPrescriptionDataDTO.getInvestigations());
            byId.setInstructions(patientPrescriptionDataDTO.getInstructions());
            byId.setS3PrescriptionPath(pdfUrl);
            byId.setUploadedBy(UploadedBy.DOCTOR);
            byId.setDocumentType("Rx_By_Your_Doctor");
            patientPrescriptionRepository.save(byId);
            saveToRecentSuggestion(patientPrescriptionDataDTO, byId.getDoctorId());

            // provision to send notification
            NotificationPrescriptionDTO notificationPrescriptionDTO = getNotificationPrescriptionDTO(doctor, byId);
            Assert.notNull(notificationPrescriptionDTO, "can not be null");
            try {
                log.info("inside try block");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<NotificationPrescriptionDTO> request = new HttpEntity<>(notificationPrescriptionDTO, headers);
                this.restTemplate.postForEntity(NOTIFICATION_SERVICE_ALERT, request, Void.class);
                // this.restTemplate.postForObject(NOTIFICATION_SERVICE_PRESCRIPTION_INFORMATION,notificationPrescriptionDTO,Void.class);
            } catch (Exception exception) {
                log.error("Error sending notification : {}", exception.getMessage());
            }
        }
    }

    private static NotificationPrescriptionDTO getNotificationPrescriptionDTO(Doctor doctor, PatientPrescription byId) {
        NotificationPrescriptionDTO notificationPrescriptionDTO = new NotificationPrescriptionDTO();
        notificationPrescriptionDTO.setNotificationType(FirebaseNotificationType.PRESCRIPTION);
        notificationPrescriptionDTO.setDoctorName(doctor.getUserInfo().getDoctorFullName());
        notificationPrescriptionDTO.setMobileNumber(byId.getMobileNumber());
        notificationPrescriptionDTO.setFamilyMemberId(byId.getFamilyMemberId());
        notificationPrescriptionDTO.setDoctorId(byId.getDoctorId());
        notificationPrescriptionDTO.setRecordId(byId.getId());
        notificationPrescriptionDTO.setIndiAppId(byId.getIndiAppId());
        return notificationPrescriptionDTO;
    }


    private static String formatAddress(String clinicAddress, int maxCharsPerLine) {
        if (clinicAddress == null) {
            return ""; // or you can choose to return a specific message or handle it differently
        }

        StringBuilder formattedAddress = new StringBuilder();
        int lineLength = 0;

        String[] addressComponents = clinicAddress.split(", ");

        for (int i = 0; i < addressComponents.length; i++) {
            String component = addressComponents[i];

            if (i == 0) {
                formattedAddress.append("\n").append(component);
                lineLength = component.length();
            } else {
                if (lineLength + component.length() + 2 <= maxCharsPerLine) {
                    formattedAddress.append(", ").append(component);
                    lineLength += component.length() + 2;
                } else {
                    formattedAddress.append("\n").append(component);
                    lineLength = component.length();
                }
            }
        }

        return formattedAddress.toString();
    }

    public static String combineVitals(List<VitalBean> vitalBeans) {
        StringBuilder result = new StringBuilder();

        for (VitalBean bean : vitalBeans) {
            String label = bean.getLabel();
            List<String> values = bean.getValues();
            String unit = bean.getUnit();
            if (label != null && values != null && !values.isEmpty() && unit != null && !containsEmptyString(values)) {
                StringBuilder combinedValue = new StringBuilder();

                for (String value : values) {
                    if (value.startsWith("BP@")) {
                        String bpValue = value.substring(3); // Remove "BP@"
                        combinedValue.append(bpValue).append("-");
                    } else {
                        combinedValue.append(value).append("-");
                    }
                }
                if (combinedValue.length() > 0) {
                    combinedValue.setLength(combinedValue.length() - 1);
                }
                String vitalString = label + "=" + combinedValue + " " + unit;
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(vitalString);
            }
        }
        return result.toString();
    }

    private static boolean containsEmptyString(List<String> list) {
        for (String s : list) {
            if (s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private File createPdf(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Doctor doctor, String entityId) throws IOException {
        String fileName = "prescription" + new DateTime().getMillis() + ".pdf";

        log.info("createPdf fileName :  {}", fileName);
        int maxCharsPerLine = 35; // Change this to your desired maximum characters per line
        int formatedAdderessLenght = 35;
        int formatedDegree = 35;
        // Two newline characters for space
        File file = new File(fileName);
        PrescriptionSettings prescriptionSettings = prescriptionSettingsRepository.findByDoctorId(doctor.getId());
        PrescriptionPrintSettings prescriptionPrintSettings = prescriptionPrintSettingsRepository.findByDoctorId(doctor.getId());
        PatientBookingRequest patientBookingRequest = patientBookingRequestRepository.findOne(patientPrescriptionDataDTO.getPatientBookingRequestId());
        CustomPatientBookingRequest customPatientBookingRequest = customPatientBookingRequestRepository.findOne(patientPrescriptionDataDTO.getPatientBookingRequestId());
        IndividualClinicAppMapping individualClinicAppMapping = individualClinicAppMappingRepository.findByMappedClinicIdsIn(entityId);
        String appName = null;
        String downloadLink = null;
        if (individualClinicAppMapping != null) {
            appName = individualClinicAppMapping.getAppName();
            downloadLink = individualClinicAppMapping.getAppDownloadLink() != null && !individualClinicAppMapping.getAppDownloadLink().isEmpty()
                    ? individualClinicAppMapping.getAppDownloadLink() : null;
        }
        boolean fileCreated = file.createNewFile();
        if (fileCreated) {
            Document document = getDocument(prescriptionPrintSettings);
            try {
                BaseFont baseFont = getBaseFont(patientPrescriptionDataDTO.getLanguageId());
                Font smallFont = new Font(baseFont, Font.NORMAL);
                BaseFont newFont = BaseFont.createFont(notoSans, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                Font intstructionFont = new Font(newFont, 10);
                Font fontTitle = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
                Font fontDetails = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
                Font fontHeader = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
                Font fontGeneric = new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC);
                Font blueFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.BLUE);

                // emr font configuration logic
                if (prescriptionPrintSettings.getPrescriptionFontConfiguration() != null) {
                    applyFontConfiguration(prescriptionPrintSettings, fontTitle, fontDetails, fontHeader, fontGeneric, intstructionFont, smallFont);
                }

                PdfWriter.getInstance(document, new FileOutputStream(fileName));
                document.open();

                // Create a LineSeparator with default properties
                LineSeparator lineSeparator = new LineSeparator();
                Chunk c1 = new Chunk(lineSeparator);
                Paragraph normalLine = new Paragraph();
                normalLine.add(c1);
                normalLine.add(lineSeparator);
                normalLine.setSpacingBefore(1f);

                DottedLineSeparator separator = new DottedLineSeparator();
                Chunk c = new Chunk(separator);
                if (prescriptionSettings.getLetterHead()) {
                    if (entityId != null) {
                        byte[] imageLogo = getImageLogo(entityId);
                        if (imageLogo != null) {
                            Image image = Image.getInstance(imageLogo);
                            image.setAlignment(Image.ALIGN_CENTER);
                            image.scalePercent(40.0f);
                            //  clinicName.add(0, image);
                            document.add(image);
                        }
                    }


                    Paragraph clinicName = new Paragraph(patientPrescriptionDataDTO.getClinicName(), fontTitle);
                    clinicName.setAlignment(Element.ALIGN_LEFT);
                    clinicName.setSpacingBefore(5f);
                    clinicName.setSpacingAfter(10f);
                    document.add(clinicName);

                    // Formatted clinic address and contact.
                    PdfPTable clinicDoctorDetails = new PdfPTable(2);
                    clinicDoctorDetails.setWidthPercentage(100);
                    String address = formatAddress(patientPrescriptionDataDTO.getClinicAddress(), formatedAdderessLenght);
                    String content = address + "\n" + "Contact  :" + patientPrescriptionDataDTO.getClinicContact();

                    PdfPCell cell = new PdfPCell(new Phrase(content, fontHeader));
                    cell.setHorizontalAlignment(PdfPCell.ALIGN_LEFT);
                    cell.setBorderWidth(0);
                    clinicDoctorDetails.addCell(cell);


                    // Create a Paragraph for the doctor's name
                    Paragraph doctorNameParagraph = new Paragraph();
                    doctorNameParagraph.add(new Chunk(patientPrescriptionDataDTO.getDoctorName(), fontTitle)); // Make doctor's name bold// Add a space
                    doctorNameParagraph.add(" \n");

                    String qualificationDegrees = doctor.getQualificationDegreeSet()
                            .stream()
                            .map(EducationDegree::getName)
                            .collect(Collectors.joining(", "));

                    String doctorDegree = formatAddress(qualificationDegrees, formatedDegree);
                    Chunk doctorDegreesChunk = new Chunk(doctorDegree, fontHeader);
                    doctorNameParagraph.add(doctorDegreesChunk);
                    PdfPCell doctorNameCell = new PdfPCell(doctorNameParagraph);
                    doctorNameCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    doctorNameCell.setBorder(Rectangle.NO_BORDER);
                    clinicDoctorDetails.addCell(doctorNameCell);
                    document.add(clinicDoctorDetails);

                    document.add(normalLine);

                }
                // Create a Paragraph for patient name and date
                Paragraph patientNameDate = new Paragraph();
                patientNameDate.setFont(fontDetails);
                patientNameDate.add("Name : " + patientPrescriptionDataDTO.getPatientName());
                patientNameDate.add(new Chunk(new VerticalPositionMark()));
                patientNameDate.add("Date : " + DateTime.now().toString("dd-MM-yyyy hh:mm:a"));

// Add patientNameDate to the document
                document.add(patientNameDate);

// Create a Paragraph for age and mobile number
                Paragraph ageMobile = new Paragraph();
                ageMobile.setFont(fontDetails);
                String age = patientPrescriptionDataDTO.getAge() != null ? patientPrescriptionDataDTO.getAge() : "N/A";
                String gender = patientPrescriptionDataDTO.getGender() != null ? patientPrescriptionDataDTO.getGender() : "N/A";
                String ageGenderInfo = "Age/Gender : " + age + "/" + gender;
                ageMobile.add(ageGenderInfo);
                ageMobile.add(new Chunk(new VerticalPositionMark()));
                String mobileNumber = patientPrescriptionDataDTO.getMobileNumber() != null ? patientPrescriptionDataDTO.getMobileNumber().toString() : "N/A";
                String mobileNumberInfo = "Mobile No : " + mobileNumber;
                ageMobile.add(mobileNumberInfo);
                document.add(ageMobile);

                document.add(normalLine);

                Paragraph vitalParagraph = new Paragraph();
                vitalParagraph.setFont(fontDetails);
                if (prescriptionSettings.getVitals()) {
                    if (patientBookingRequest != null) {
                        if (patientBookingRequest.getVitalBeanList() != null && !patientBookingRequest.getVitalBeanList().isEmpty()) {
                            vitalParagraph.add(combineVitals(patientBookingRequest.getVitalBeanList()));
                            document.add(vitalParagraph);
                        }
                    } else if (customPatientBookingRequest != null) {
                        if (customPatientBookingRequest.getVitalBeanList() != null && !customPatientBookingRequest.getVitalBeanList().isEmpty()) {
                            vitalParagraph.add(combineVitals(customPatientBookingRequest.getVitalBeanList()));
                            document.add(vitalParagraph);
                        }
                    }
                }
                //adding symptoms
                if (prescriptionSettings.getSymptoms()) {
                    document.add(generateSymptomsParagraph(patientPrescriptionDataDTO, fontDetails, fontTitle));
                }

                //adding clinical finding
                if (prescriptionSettings.getClinicalFinding()) {
                    document.add(generateClinicalFindingsParagraph(patientPrescriptionDataDTO, fontDetails, fontTitle));
                }

                // adding diagnosis
                if (prescriptionSettings.getDiagnosis()) {
                    document.add(generateDiagnosisParagraph(patientPrescriptionDataDTO, fontDetails, fontTitle));
                }
                //add space
                document.add(addSpace());
                //add medicine table
                PdfPTable table = generateMedicineTable(patientPrescriptionDataDTO, fontTitle, fontDetails, fontGeneric, smallFont, prescriptionSettings);
                if (table != null) {
                    document.add(table);
                }

                if (prescriptionSettings.getMedicalInfo()) {
                    String specialityName = patientPrescriptionDataDTO.getSpecialityName();
                    if (specialityName != null) {
                        switch (specialityName) {
                            case "Gynecologist":
                                document.add(addSpace());
                                Paragraph medicalInfo = addMedicalInfo(patientPrescriptionDataDTO.getFamilyMemberId(), fontTitle, fontDetails);
                                if (medicalInfo != null) {
                                    document.add(medicalInfo);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }


                //add Investigations
                if (patientPrescriptionDataDTO.getInvestigations() != null && !patientPrescriptionDataDTO.getInvestigations().isEmpty()) {
                    document.add(generateInvestigationsParagraph(patientPrescriptionDataDTO, fontTitle, fontDetails));
                }
                //add GeneralInstructions
                if (prescriptionSettings.getInstruction()) {
                    document.add(generateGeneralInstructionsParagraph(patientPrescriptionDataDTO, fontTitle, intstructionFont));
                }
                // saving private instruction
                savePrivateInstruction(patientPrescriptionDataDTO.getPrivateInstruction(),
                        entityId,
                        doctor.getId(),
                        patientPrescriptionDataDTO.getFamilyMemberId()
                );

                if (patientPrescriptionDataDTO.getFollowupDate() != null && !patientPrescriptionDataDTO.getFollowupDate().toString().isEmpty()) {
                    Paragraph followup = new Paragraph();
                    followup.setSpacingBefore(10f);
                    followup.setAlignment(Element.ALIGN_LEFT);
                    followup.setFont(fontDetails);
                    followup.add("Next Follow-up");

                    Paragraph followupDate = new Paragraph();
                    followupDate.setFont(fontDetails);
                    followupDate.setAlignment(Element.ALIGN_LEFT);

                    // Convert the follow-up date to Indian time zone and format it
                    String formattedDate = DateTimeFormat.forPattern("dd-MMM-yyyy, EEEE")
                            .withLocale(new Locale("en", "IN"))
                            .print(patientPrescriptionDataDTO.getFollowupDate().withZone(DateTimeZone.forID("Asia/Kolkata")));

// Add the formatted date to the followupDate list or array
                    followupDate.add("Date: " + formattedDate);

                    document.add(followup);
                    document.add(followupDate);

                }

                if (patientPrescriptionDataDTO.getFollowupNote() != null && !patientPrescriptionDataDTO.getFollowupNote().isEmpty()) {
                    Paragraph followupNote = new Paragraph();
                    followupNote.setFont(fontDetails);
                    followupNote.setAlignment(Element.ALIGN_LEFT);
                    followupNote.add("Followup Note:- " + patientPrescriptionDataDTO.getFollowupNote());
                    document.add(followupNote);
                }

                if (patientPrescriptionDataDTO.getReferredDoctorName() != null) {
                    Paragraph referralDoctor = new Paragraph();
                    referralDoctor.setFont(fontDetails);
                    referralDoctor.setAlignment(Element.ALIGN_LEFT);
                    referralDoctor.add("Referred To:- " + patientPrescriptionDataDTO.getReferredDoctorName());
                    document.add(referralDoctor);
                }


                Paragraph signatureDetails = new Paragraph();
                signatureDetails.setFont(fontTitle);
                signatureDetails.setAlignment(Element.ALIGN_LEFT);
                signatureDetails.add(patientPrescriptionDataDTO.getDoctorName());

                Paragraph degreeDetails = new Paragraph();
                degreeDetails.setFont(fontHeader);
                degreeDetails.setAlignment(Element.ALIGN_LEFT);
                String degreetext = (doctor.getDoctorSpecificDegree());
                String delimiter = ","; // Change this to your desired delimiter

                if (degreetext == null || degreetext.trim().isEmpty()) {
                    degreetext = doctor.getQualificationDegreeSet().stream().map(EducationDegree::getName).collect(Collectors.joining(delimiter));
                }

                String[] lines = splitTextIntoLines(degreetext, maxCharsPerLine);
                for (String line : lines) {
                    degreeDetails.add(line);
                }


                if (prescriptionSettings.getSignature()) {
                    PdfPTable signatureTable = new PdfPTable(3);
                    signatureTable.setWidthPercentage(100);
                    signatureTable.setSpacingBefore(10f);

                    PdfPCell appNameCell = new PdfPCell();
                    appNameCell.setBorder(Rectangle.NO_BORDER);
                    appNameCell.addElement(createAppNameParagraph(appName, blueFont));
                    appNameCell.addElement(createQRCodeImage(downloadLink));
                    signatureTable.addCell(appNameCell);

                    PdfPCell emptyCell = new PdfPCell();
                    emptyCell.setBorder(Rectangle.NO_BORDER);
                    signatureTable.addCell(emptyCell);

                    PdfPCell signatureCell = new PdfPCell();
                    signatureCell.setBorder(Rectangle.NO_BORDER);
                    signatureCell.addElement(signatureDetails);
                    signatureCell.addElement(degreeDetails);
                    signatureTable.addCell(signatureCell);

                    document.add(signatureTable);
                } else {
                    document.add(createAppNameParagraph(appName, blueFont));
                    document.add(createQRCodeImage(downloadLink));
                }

                document.close();
                return file;


            } catch (DocumentException e) {
                log.error("Exception creating document:{}", e.getMessage());
            } catch (FileNotFoundException e) {
                log.error("File not found:{}", e.getMessage());
            } catch (Exception ex) {
                log.error("Exception :{}", ex.getMessage());
            }
            return null;
        } else {
            log.info("File not created..");
        }
        return null;
    }

    private static Document getDocument(PrescriptionPrintSettings prescriptionPrintSettings) {
        Document document = new Document();
        if (prescriptionPrintSettings != null) {
            if (prescriptionPrintSettings.getMarginBottom() != null &&
                    prescriptionPrintSettings.getMarginTop() != null &&
                    prescriptionPrintSettings.getMarginRight() != null &&
                    prescriptionPrintSettings.getMarginLeft() != null
            ) {
                document.setMargins(prescriptionPrintSettings.getMarginLeft() * 36F, prescriptionPrintSettings.getMarginRight() * 36F,
                        prescriptionPrintSettings.getMarginTop() * 36F, prescriptionPrintSettings.getMarginBottom() * 36F);
            }
        }
        return document;
    }


    private String getFrequency(MedicineDTO medicineDTO, String languageId) {
        String frequency = "";
        List<MedicineDosage> medicineDosageList = medicineDTO.getMedicineDosageList();
        frequency = medicineDosageList.stream().map(medicineDosage -> getPreparedFrequency(medicineDosage, languageId)).collect(Collectors.joining("\n\n" + getTranslation(languageId, "After That") + "\n\n")) + "\n\n";
        return frequency;
    }


    private String getPreparedFrequency(MedicineDosage m, String languageId) {
        Dose dose = m.getDose();
        String dispensingUnit = m.getDispensingUnit() != null ? m.getDispensingUnit() : "";
        String quantity = m.getQuantity() != null ? getFormattedQuantity(m.getQuantity()) : "";
        String frequency = quantity + " " + dispensingUnit;
        String translatedFrequency = "";

        StringBuilder routeString = new StringBuilder();
        if (m.getSelectedRoutes() != null && !m.getSelectedRoutes().isEmpty()) {
            routeString.append("\n- ");
            routeString.append(String.join(", ", m.getSelectedRoutes()));
        }

        // String sosOrTillRequired = m.getSosOrTillRequired() != null ? "-" + m.getSosOrTillRequired() : "";
        switch (dose.getFrequencyType()) {
            case 0:
                translatedFrequency = " " + getTranslatedFrequencyForDoseO(languageId, dose) + " ";
                break;

            case 1:
                translatedFrequency = getTranslatedFrequencyForDose1(languageId, dose) + " ";
                break;

            case 2:
                translatedFrequency = getTranslatedFrequencyForDose2(languageId, dose, dispensingUnit);
                break;

            case 3:
                translatedFrequency = getTranslatedFrequencyForDose3(languageId,dose,dispensingUnit);
                break;
        }
        if (dose.getFrequencyType() == 2 || dose.getFrequencyType() == 3) {
            return translatedFrequency + routeString + "\n";
        }
        return frequency + " " + translatedFrequency + routeString + "\n";
    }

    private String getTranslatedFrequencyForDose2(String languageId, Dose dose, String dispensingUnit) {
        StringBuilder result = new StringBuilder();

        if (dose.getF3CheckedDayTimes() != null && !dose.getF3CheckedDayTimes().isEmpty()) {
            List<String> checkedDayTimes = dose.getF3CheckedDayTimes();
            String translatedDayTime = null;

            int size = checkedDayTimes.size();
            for (int i = 0; i < size; i++) {
                String dayTime = checkedDayTimes.get(i);
                // Check if the field is populated for the given day time
                if (dayTime.equalsIgnoreCase("Breakfast")) {
                    translatedDayTime = getTranslation(languageId, dayTime);
                    if ((translatedDayTime != null && !translatedDayTime.isEmpty())) {
                        translatedDayTime = " @" + translatedDayTime;
                    }
                    result.append(dose.getF3BreakfastTabs()).append(dispensingUnit).append(translatedDayTime);
                    if (dose.getF3BreakfastRelation() != null && !dose.getF3BreakfastRelation().isEmpty()) {
                        if (!dose.getF3BreakfastRelation().equalsIgnoreCase("None")) {
                            result.append(" (").append(getTranslation(languageId, dose.getF3BreakfastRelation())).append(")");
                        }
                    }

                } else if (dayTime.equalsIgnoreCase("Lunch")) {
                    translatedDayTime = getTranslation(languageId, dayTime);
                    if ((translatedDayTime != null && !translatedDayTime.isEmpty())) {
                        translatedDayTime = " @" + translatedDayTime;
                    }
                    result.append(dose.getF3LunchTabs()).append(dispensingUnit).append(translatedDayTime);
                    if (dose.getF3LunchRelation() != null && !dose.getF3LunchRelation().isEmpty()) {
                        if (!dose.getF3LunchRelation().equalsIgnoreCase("None")) {
                            result.append(" (").append(getTranslation(languageId, dose.getF3LunchRelation())).append(")");
                        }
                    }

                } else if (dayTime.equalsIgnoreCase("Dinner")) {
                    translatedDayTime = getTranslation(languageId, dayTime);
                    if ((translatedDayTime != null && !translatedDayTime.isEmpty())) {
                        translatedDayTime = " @" + translatedDayTime;
                    }
                    result.append(dose.getF3DinnerTabs()).append(" ").append(dispensingUnit).append(translatedDayTime);
                    if (dose.getF3DinnerRelation() != null && !dose.getF3DinnerRelation().isEmpty()) {
                        if (!dose.getF3DinnerRelation().equalsIgnoreCase("None")) {
                            result.append(" (").append(getTranslation(languageId, dose.getF3DinnerRelation())).append(")");
                        }
                    }
                }

                // Add comma only if it's not the last iteration
                if (i < size - 1) {
                    result.append(", ");
                }
            }

        }
        return result.toString().trim();
    }

    private String getTranslatedFrequencyForDose3(String languageId, Dose dose, String dispensingUnit) {
        StringBuilder result = new StringBuilder();

        if (dose.getF4CheckedDayTimes() != null && !dose.getF4CheckedDayTimes().isEmpty()) {
            List<String> checkedDayTimes = dose.getF4CheckedDayTimes();
            String translatedDayTime = null;

            for (int i = 0; i < checkedDayTimes.size(); i++) {
                String dayTime = checkedDayTimes.get(i);
                // Check if the field is populated for the given day time
                if (dayTime.equalsIgnoreCase("Morning")) {
                    translatedDayTime = getTranslation(languageId,dayTime) ;
                    if((translatedDayTime != null && !translatedDayTime.isEmpty()) ){
                        translatedDayTime = "@"+translatedDayTime;
                    }
                    result.append(dose.getF4MorningTabs()).append(" ").append(dispensingUnit).append(" ").append(translatedDayTime);
                } else if (dayTime.equalsIgnoreCase("Afternoon")) {
                    translatedDayTime = getTranslation(languageId,dayTime) ;
                    if((translatedDayTime != null && !translatedDayTime.isEmpty()) ){
                        translatedDayTime = "@"+translatedDayTime;
                    }
                    result.append(dose.getF4AfternoonTabs()).append(" ").append(dispensingUnit).append(" ").append(translatedDayTime);
                } else if (dayTime.equalsIgnoreCase("Evening")) {
                    translatedDayTime = getTranslation(languageId,dayTime) ;
                    if((translatedDayTime != null && !translatedDayTime.isEmpty()) ){
                        translatedDayTime = "@"+translatedDayTime;
                    }
                    result.append(dose.getF4EveningTabs()).append(dispensingUnit).append(" ").append(translatedDayTime);
                } else if (dayTime.equalsIgnoreCase("Night")){
                    translatedDayTime = getTranslation(languageId,dayTime) ;
                    if((translatedDayTime != null && !translatedDayTime.isEmpty()) ){
                        translatedDayTime = "@"+translatedDayTime;
                    }
                    result.append(dose.getF4NightTabs()).append(" ").append(dispensingUnit).append(" ").append(translatedDayTime);
                }

                // Append comma if it's not the last iteration
                if (i < checkedDayTimes.size() - 1) {
                    result.append(", ");
                }
            }

            if (dose.getF4FoodRelation() != null && !dose.getF4FoodRelation().isEmpty()) {
                result.append(" (").append(getTranslation(languageId,dose.getF4FoodRelation())).append(")");
            }
        }
        return result.toString().trim();
    }

    private String getTranslatedFrequencyForDose1(String languageId, Dose dose) {
        String finalFoodBedString = "";
        String bedTime = dose.getBedTime() ? getTranslation(languageId, "Bed Time") : "";
        String timesString = dose.getF2Times() != null ? getTranslation(languageId, dose.getF2Times().split(" ")[1]) : "";
        String duration = getTranslation(languageId, dose.getF2duration());
        String foodRelation = getTranslation(languageId, dose.getF2FoodRelation());
        if (foodRelation != null && !foodRelation.isEmpty()) {
            if (bedTime != null && !bedTime.isEmpty()) {
                finalFoodBedString = " (" + foodRelation + "," + bedTime + ")";
            } else {
                finalFoodBedString = " (" + foodRelation + ")";
            }
        } else {
            if (bedTime != null && !bedTime.isEmpty()) {
                finalFoodBedString = " (" + bedTime + ")";
            }
        }
        return getF2Times(dose.getF2Times()) + " " + timesString + " " + duration + " " + finalFoodBedString;

    }

    private String getF2Times(String f2Times) {
        if (f2Times != null) {
            return f2Times.split(" ")[0];
        } else {
            return "";
        }
    }

    private String getTranslatedFrequencyForDoseO(String languageId, Dose dose) {
        String finalFoodBedString = "";
        String dayTimes = dose.getF1CheckedDayTimes().stream().map(d -> getTranslation(languageId, d)).collect(Collectors.joining(" + "));
        String foodRelation = getTranslation(languageId, dose.getF1FoodRelation());
        String bedTime = dose.getBedTime() ? getTranslation(languageId, "Bed Time") : "";

        if (foodRelation != null && !foodRelation.isEmpty()) {
            if (bedTime != null && !bedTime.isEmpty()) {
                finalFoodBedString = " (" + foodRelation + "," + bedTime + ")";
            } else {
                finalFoodBedString = " (" + foodRelation + ")";
            }
        } else {
            if (bedTime != null && !bedTime.isEmpty()) {
                finalFoodBedString = " (" + bedTime + ")";
            }
        }
        return dayTimes + finalFoodBedString;
    }

    private String getTranslation(String languageId, String textToBeTranslated) {
        if (textToBeTranslated == null) {
            return "";
        }
        log.info("Translating text--------" + textToBeTranslated);
        String translation;
        CmsTranslation cmsTranslation = cmsTranslationRepository.findByNameIgnoreCase(textToBeTranslated.toUpperCase());
        if (cmsTranslation != null) {
            translation = cmsTranslation.getTranslations().stream().filter(c -> c.getLanguageId().equals(languageId)).findFirst().get().getLanguageTranslation();
            log.info(translation);
            return translation;
        }
        return textToBeTranslated;
    }

    private String translateDuration(String languageId, String duration) {
        if (duration != null && !duration.isEmpty()) {
            if (duration.equalsIgnoreCase("SOS") || duration.equalsIgnoreCase("Till required")) {
                return getTranslation(languageId, duration);
            } else {
                String[] parts = duration.split(" ");
                if (parts.length == 2) {
                    String value = parts[0];
                    String unit = parts[1];
                    String translatedUnit = getTranslation(languageId, unit);
                    return value + " " + translatedUnit;
                } else {
                    return duration;
                }
            }
        } else {
            return "";
        }
    }



    private String getInstructions(Instruction s, String languageId) {
        try {
            Optional<MultipleLanguageDTO> multipleLanguageDTO = s.getMultipleLanguage().stream()
                    .filter(i -> i.getLanguageId().equals(languageId))
                    .findFirst();
            if (multipleLanguageDTO.isPresent()) {
                return multipleLanguageDTO.get().getInstruction();
            } else {
                return s.getInstructions();
            }

        } catch (Exception e) {
            return s.getInstructions();
        }
    }

    private String[] splitTextIntoLines(String text, int maxCharsPerLine) {
        if (text == null) {
            return new String[0];
        }

        List<String> lines = new ArrayList<>();
        int length = text.length();
        int startIndex = 0;
        while (startIndex < length) {
            int endIndex = startIndex + maxCharsPerLine;
            if (endIndex >= length) {
                endIndex = length;  // Ensure we don't go beyond the end of the text
            } else {
                // Find the last space before the endIndex
                while (endIndex > startIndex && text.charAt(endIndex) != ' ') {
                    endIndex--;
                }
                if (endIndex == startIndex) {
                    // No space found within the maxCharsPerLine limit, so split at endIndex
                    endIndex = startIndex + maxCharsPerLine;
                }
            }

            lines.add(text.substring(startIndex, endIndex).trim());
            startIndex = endIndex;
        }
        return lines.toArray(new String[0]);
    }


    private void savePrivateInstruction(String privateInstructionText, String entityId, String doctorId, String familyMemberId) {
        if (privateInstructionText != null && !privateInstructionText.isEmpty()) {
            PrivateInstruction privateInstruction = new PrivateInstruction();
            privateInstruction.setPrivateInstruction(privateInstructionText);
            privateInstruction.setEntityId(entityId);
            privateInstruction.setFamilyMemberId(familyMemberId);
            privateInstruction.setDoctorId(doctorId);
            privateInstructionRepository.save(privateInstruction);
        }
    }


    @Override
    public String uploadPrescriptionInfoImageFileToS3(MultipartFile file, String prescriptionInfoId) {
        try {
            File thumbnailFile = this.createThumbnail(file, prescriptionInfoId);

            String s3ObjectName = String.format("%s.%s", prescriptionInfoId, THUMBNAIL_OUTPUT_FORMAT);
            // Upload to S3
            this.amazonS3Client.putObject(this.s3BucketNamePatientPrescription + "/prescriptionInfo", s3ObjectName, thumbnailFile);

            return s3ObjectName;
        } catch (IOException ioException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("IO exception while processing photo: [ %s ]", ioException.getMessage()),
                    ioException);
        } catch (InterruptedException interruptedException) {
            throw new PatientPrescriptionPhotoProcessingException(String
                    .format("Interrupted exception while processing photo: [ %s ]", interruptedException.getMessage()),
                    interruptedException);
        } catch (IM4JavaException im4JavaException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("Imagemagick For Java exception while processing photo: [ %s ]",
                            im4JavaException.getMessage()),
                    im4JavaException);
        } catch (AmazonServiceException amazonServiceException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("Amazon Service exception while processing photo: [ %s ]",
                            amazonServiceException.getMessage()),
                    amazonServiceException);
        } catch (SdkClientException sdkclientException) {
            throw new PatientPrescriptionPhotoProcessingException(
                    String.format("AWS SDK exception while processing photo: [ %s ]", sdkclientException.getMessage()),
                    sdkclientException);
        }
    }

    @Override
    public PrescriptionInfo getPrescriptionInfoById(String prescriptionInfoId) {
        return prescriptionInfoReposirtory.findOne(prescriptionInfoId);
    }

    @Override
    public PrescriptionInfo updatePrescriptionInfo(String prescriptionInfoId, PrescriptionInfoDTO prescriptionInfoDTO) {
        PrescriptionInfo prescriptionInfo = prescriptionInfoReposirtory.findOne(prescriptionInfoId);
        if (prescriptionInfo != null) {
            BeanUtils.copyProperties(prescriptionInfoDTO, prescriptionInfo);
            return prescriptionInfoReposirtory.save(prescriptionInfo);
        } else {
            throw new RuntimeException("Prescription Info not found");
        }
    }


    @Override
    public InputStream downloadPrescriptionInfoFromS3ForReceipt(String s3Path) {
        try {
            String s3ObjectName = String.format("%s.%s", s3Path, THUMBNAIL_OUTPUT_FORMAT);
            S3Object s3object = null;

            s3object = this.amazonS3Client.getObject(this.s3BucketNamePatientPrescription + "/prescriptionInfo", s3ObjectName);

            return s3object.getObjectContent();
        } catch (AmazonServiceException amazonServiceException) {
            throw new RuntimeException(
                    String.format("Amazon Service exception while downloading prescriptionInfo [ %s ] pdf: [ %s ]", s3Path,
                            amazonServiceException.getMessage()),
                    amazonServiceException);
        } catch (SdkClientException sdkclientException) {
            throw new RuntimeException(
                    String.format("AWS SDK exception while downloading prescriptionInfo [ %s ] pdf: [ %s ]", s3Path,
                            sdkclientException.getMessage()),
                    sdkclientException);
        }
    }

    @Override
    public PrescriptionInfo getPrescriptionInfo(String doctorId, String entityId) {
        PrescriptionInfo prescriptionInfo = prescriptionInfoReposirtory.getByDoctorIdAndEntityId(doctorId, entityId);
        if (prescriptionInfo != null) {
            Doctor doctor = doctorRepository.findOne(doctorId);
            Entity entity = entityRepository.findOne(entityId);
            prescriptionInfo.setIndiClinicAppId(entity.getAppId() != null ? entity.getAppId() : "");
            prescriptionInfo.setDoctorName(doctor.getUserInfo().getDoctorFullName());

            String degreetext = (doctor.getDoctorSpecificDegree());
            if (degreetext == null || degreetext.trim().isEmpty()) {
                // Map degree names assuming primary degree is first
                return prescriptionInfo;
            } else {
                prescriptionInfo.setDoctorDegree(doctor.getDoctorSpecificDegree());
            }
        }

        return prescriptionInfo;
    }

    @Override
    public Page<PatientPrescription> getPatientPrescriptionFamilyMemberId(String familyMemberId, int page, int size) {
        Pageable pageable = new PageRequest(page, size, Sort.Direction.DESC, CommonConstants.DOCUMENT_CREATED_AT_FIELD_NAME);

        return patientPrescriptionRepository.findByFamilyMemberIdAndIndiAppId(familyMemberId, null, pageable);
    }

    @Override
    public Page<PatientPrescription> getPatientPrescriptionFamilyMemberIdAndIndiAppId(String familyMemberId, String indiAppId, int page, int size) {
        Pageable pageable = new PageRequest(page, size, Sort.Direction.DESC, CommonConstants.DOCUMENT_CREATED_AT_FIELD_NAME);
        return patientPrescriptionRepository.findByFamilyMemberIdAndIndiAppId(familyMemberId, indiAppId, pageable);
    }

    @Override
    public PrescriptionInfo createSubstitutePrescriptionInfo(String doctorId, String entityId) {
        Doctor doctor = doctorRepository.findOne(doctorId);
        Entity entity = entityRepository.findOne(entityId);

        PrescriptionInfo prescriptionInfo = new PrescriptionInfo();
        prescriptionInfo.setDoctorId(doctorId);
        prescriptionInfo.setEntityId(entityId);
        prescriptionInfo.setDoctorName(doctor.getUserInfo().getDoctorFullName());
        prescriptionInfo.setDoctorDegree(doctor.getQualificationDegreeSet().stream().map(EducationDegree::getName).collect(Collectors.joining(", ")));
        prescriptionInfo.setEmail(doctor.getEmailId());
        prescriptionInfo.setClinicName(entity.getName());
        prescriptionInfo.setClinicAddress(entity.getAddressLine1() + ", " + entity.getAddressLine2() + ", " + entity.getArea() + " " + entity.getCity());
        prescriptionInfo.setClinicContact(entity.getMobileNumber() + "");
        prescriptionInfo.setRegNo(doctor.getRegistrationNumber());
        prescriptionInfo.setIndiClinicAppId(entity.getAppId() != null ? entity.getAppId() : "");
        prescriptionInfo.setDoctorMobileNumber(doctor.getUserInfo().getMobileNumber());
        return prescriptionInfoReposirtory.save(prescriptionInfo);
    }

    @Override
    public InputStream downloadPrescriptionImageFromS3ForReceipt(String s3Path) {
        try {
            String s3ObjectName = String.format("%s.%s", s3Path, THUMBNAIL_OUTPUT_FORMAT);
            S3Object s3object = null;

            s3object = this.amazonS3Client.getObject(this.s3BucketNamePatientPrescription + "/pdf", s3ObjectName);

            return s3object.getObjectContent();
        } catch (AmazonServiceException amazonServiceException) {
            throw new RuntimeException(
                    String.format("Amazon Service exception while downloading prescription image [ %s ] png: [ %s ]", s3Path,
                            amazonServiceException.getMessage()),
                    amazonServiceException);
        } catch (SdkClientException sdkclientException) {
            throw new RuntimeException(
                    String.format("AWS SDK exception while downloading prescription image [ %s ] png: [ %s ]", s3Path,
                            sdkclientException.getMessage()),
                    sdkclientException);
        }
    }

    private File createThumbnail(MultipartFile inputFilefile, String prescriptionId)
            throws IOException, InterruptedException, IM4JavaException {
        IMOperation op = new IMOperation();
        op.addImage(STANDARD_IN_CHANNEL); // read from stdinop.
        op.resize(this.thumbnailSize, this.thumbnailSize);
        op.gravity(THUMBNAIL_GRAVITY_CENTER);
        op.crop(this.thumbnailSize, this.thumbnailSize, 0, 0);
        // write to stdout in png-format
        op.addImage(String.format("%s:%s", THUMBNAIL_OUTPUT_FORMAT, STANDARD_IN_CHANNEL));

        // set up pipe(s): you can use one or two pipe objects
        File outputTempFile = File.createTempFile(String.format("%s-", prescriptionId), TEMPORARY_FILE_EXTENSION);
        FileOutputStream fos = new FileOutputStream(outputTempFile);

        Pipe pipeIn = new Pipe(inputFilefile.getInputStream(), null);
        Pipe pipeOut = new Pipe(null, fos);

        // set up command
        ConvertCmd convert = new ConvertCmd();
        convert.setInputProvider(pipeIn);
        convert.setOutputConsumer(pipeOut);
        // convert.createScript("myscript.sh", op);
        convert.run(op);
        fos.close();

        return outputTempFile;
    }

    @Override
    public void deleteUnregisteredDoctor(String unRegisteredDoctorId) {
        UnRegisteredDoctor unRegisteredDoctor = unregisteredDoctorRepository.findOne(unRegisteredDoctorId);
        if (unRegisteredDoctor != null) {
            unregisteredDoctorRepository.delete(unRegisteredDoctor);
        }
    }

    @Override
    public void deletePrescription(String prescriptionId) {
        PatientPrescription patientPrescription = patientPrescriptionRepository.findOne(prescriptionId);
        if (patientPrescription != null) {
            patientPrescriptionRepository.delete(patientPrescription);
        }
    }

    @Override
    public List<UnRegisteredDoctor> getUnregisteredDoctorsList(Long patientMobileNumber) {
        return unregisteredDoctorRepository.findByAddedBy(patientMobileNumber);
    }

    @Override
    public List<MedicalRecordDocumentTypeResource> getDocumentTypes() {
        List<MedicalRecordDocumentType> documentTypes = medicalRecordDocumentTypeRepository.findAll();
        List<MedicalRecordDocumentTypeResource> documentTypeResources = new ArrayList<>();
        for (MedicalRecordDocumentType documentType : documentTypes) {
            MedicalRecordDocumentTypeResource documentTypeResource = new MedicalRecordDocumentTypeResource();
            documentTypeResource.setMedicalRecordId(documentType.getId());
            documentTypeResource.setDocumentType(documentType.getDocumentType());
            documentTypeResources.add(documentTypeResource);
        }

        return documentTypeResources;
    }

    @Override
    public MedicalRecordDocumentTypeResource addDocumentType(String documentType) {
        MedicalRecordDocumentType newDocumentType = new MedicalRecordDocumentType();
        newDocumentType.setDocumentType(documentType);
        newDocumentType = medicalRecordDocumentTypeRepository.save(newDocumentType);
        MedicalRecordDocumentTypeResource medicalRecordDocumentTypeResource = new MedicalRecordDocumentTypeResource();
        medicalRecordDocumentTypeResource.setMedicalRecordId(newDocumentType.getId());
        medicalRecordDocumentTypeResource.setDocumentType(newDocumentType.getDocumentType());
        return medicalRecordDocumentTypeResource;
    }

    @Override
    public Page<PatientPrescription> getPatPatientPrescriptionByFamilyMemberIdAndDocumentType(FilterPrescriptionDTO filterPrescriptionDTO, int page, int size) {
        Pageable pageable = new PageRequest(page, size, Sort.Direction.DESC, CommonConstants.DOCUMENT_CREATED_AT_FIELD_NAME);
        switch (filterPrescriptionDTO.getAppRole()) {
            case PATIENT:
                if (filterPrescriptionDTO.getDoctorId() != null && filterPrescriptionDTO.getFamilyMemberId() != null && filterPrescriptionDTO.getDocumentType() != null) {
                    return patientPrescriptionRepository.findByFamilyMemberIdAndDocumentTypeAndDoctorIdOrFamilyMemberIdAndDocumentTypeAndUnRegisteredDoctorId(filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDocumentType(), filterPrescriptionDTO.getDoctorId(), filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDocumentType(), filterPrescriptionDTO.getDoctorId(), pageable);
                } else if (filterPrescriptionDTO.getDocumentType() != null && !filterPrescriptionDTO.getDocumentType().isEmpty()) {
                    return patientPrescriptionRepository.findByFamilyMemberIdAndDocumentType(filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDocumentType(), pageable);
                } else if (filterPrescriptionDTO.getDoctorId() != null && !filterPrescriptionDTO.getDoctorId().isEmpty()) {
                    return patientPrescriptionRepository.findByFamilyMemberIdAndDoctorIdOrFamilyMemberIdAndUnRegisteredDoctorId(filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDoctorId(), filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDoctorId(), pageable);
                } else if (filterPrescriptionDTO.getFamilyMemberId() != null && !filterPrescriptionDTO.getFamilyMemberId().isEmpty()) {
                    return patientPrescriptionRepository.findByFamilyMemberId(filterPrescriptionDTO.getFamilyMemberId(), pageable);
                }
                return null;

            case DOCTOR:
                if (filterPrescriptionDTO.getDoctorId() != null && filterPrescriptionDTO.getFamilyMemberId() != null && filterPrescriptionDTO.getDocumentType() != null) {
                    List<PatientPrescription> patientPrescriptionList = new ArrayList<PatientPrescription>();
                    Page<PatientPrescription> patientPrescriptions = patientPrescriptionRepository.findByFamilyMemberIdAndDocumentTypeAndDoctorId(filterPrescriptionDTO.getFamilyMemberId(), filterPrescriptionDTO.getDocumentType(), filterPrescriptionDTO.getDoctorId(), pageable);
                    if (patientPrescriptions != null && patientPrescriptions.hasContent()) {
                        patientPrescriptionList.addAll(patientPrescriptions.getContent());
                    }
                    Page<PatientPrescription> patientSpecificPatientRecord = patientPrescriptionRepository.findByFamilyMemberIdAndDoctorIdAndDocumentType(filterPrescriptionDTO.getFamilyMemberId(), "null", filterPrescriptionDTO.getDocumentType(), pageable);

                    if (patientSpecificPatientRecord != null && patientSpecificPatientRecord.hasContent()) {
                        patientPrescriptionList.addAll(patientSpecificPatientRecord.getContent());
                    }
                    return new PageImpl<>(patientPrescriptionList, pageable, patientPrescriptionList.size());

                } else if (filterPrescriptionDTO.getFamilyMemberId() != null && !filterPrescriptionDTO.getFamilyMemberId().isEmpty()) {
                    List<PatientPrescription> patientPrescriptionList = new ArrayList<>();
                    Page<PatientPrescription> patientPrescriptions = patientPrescriptionRepository.findByDoctorIdAndFamilyMemberId(filterPrescriptionDTO.getDoctorId(), filterPrescriptionDTO.getFamilyMemberId(), pageable);
                    if (patientPrescriptions != null && patientPrescriptions.hasContent()) {
                        patientPrescriptionList.addAll(patientPrescriptions.getContent());
                    }
                    Page<PatientPrescription> patientSpecificPatientRecord = patientPrescriptionRepository.findByFamilyMemberIdAndDoctorId(filterPrescriptionDTO.getFamilyMemberId(), "null", pageable);
                    if (patientSpecificPatientRecord != null && patientSpecificPatientRecord.hasContent()) {
                        patientPrescriptionList.addAll(patientSpecificPatientRecord.getContent());
                    }
                    return new PageImpl<>(patientPrescriptionList, pageable, patientPrescriptionList.size());

                } else if (filterPrescriptionDTO.getDocumentType() != null && !filterPrescriptionDTO.getDocumentType().isEmpty()) {
                    return patientPrescriptionRepository.findByDoctorIdAndDocumentType(filterPrescriptionDTO.getDoctorId(), filterPrescriptionDTO.getDocumentType(), pageable);
                } else if (filterPrescriptionDTO.getDoctorId() != null && !filterPrescriptionDTO.getDoctorId().isEmpty()) {
                    return patientPrescriptionRepository.findByDoctorId(filterPrescriptionDTO.getDoctorId(), pageable);
                }
                return null;

            default:
                return null;
        }

    }

    public void deleteDocumentTypeById(String documentId) {
        MedicalRecordDocumentType medicalRecordDocumentType = medicalRecordDocumentTypeRepository.findOne(documentId);
        if (medicalRecordDocumentType != null) {
            this.medicalRecordDocumentTypeRepository.delete(medicalRecordDocumentType);
        }

    }

    @Async("masterTaskExecutor")
    public void sendEmailAsync(File file, String emailId, String s3FileKeyName, String patientName) throws Exception {
        sendEmail(file, emailId, s3FileKeyName, patientName);
    }

    private void sendEmail(File file, String emailId, String s3FileKeyName, String patientName) throws Exception {

        log.info("Sending mail please wait..............");
        String smtpHost = "smtp.gmail.com"; //replace this with a valid host
        int smtpPort = 587; //replace this with a valid port

        String sender = "quphelpdesk@gmail.com"; //replace this with a valid sender email address
        /*String password = "qupapp123*";*/
        String password = "wzwgcciwzihhdpmv";

        Properties properties = new Properties();
        properties.put("mail.smtp.host", smtpHost);
        properties.put("mail.smtp.port", smtpPort);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");

        Authenticator auth = new Authenticator() {
            //override the getPasswordAuthentication method
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {

                return new PasswordAuthentication(sender, password);
            }

        };

        Session session = Session.getDefaultInstance(properties, auth);


        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailId));
        message.setSubject(String.format("Your Prescription - %s", patientName));
        BodyPart messageBodyPart = new MimeBodyPart();
        String messageBody = String.format("Dear  %s", patientName) + ",\n\n"
                + "I trust you're doing well. Following our recent consultation, \n" +
                "I've prepared your prescription in PDF format, which is attached to this email.\n" +
                " Please find the details of your medication and treatment plan below:";

        messageBodyPart.setText(messageBody);

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
        messageBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(file);
        messageBodyPart.setDataHandler(new DataHandler(source));
        messageBodyPart.setFileName(s3FileKeyName);
        multipart.addBodyPart(messageBodyPart);

        message.setContent(multipart);
        Transport.send(message);

        log.info("Sent message Successfully..............");
    }

    @Bean(name = "masterTaskExecutor")
    @Primary
    public TaskExecutor threadPoolTaskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setThreadNamePrefix("task_executor_thread_master-");
        executor.initialize();

        return executor;
    }

    private Paragraph generateSymptomsParagraph(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font fontDetails, Font lable) {
        Paragraph paragraph = new Paragraph();
        Chunk labelChunk = new Chunk("Symptoms : ", lable);

        if (patientPrescriptionDataDTO.getSymptoms() != null && !patientPrescriptionDataDTO.getSymptoms().isEmpty()) {
            StringBuilder symText = new StringBuilder();
            for (int i = 0; i < patientPrescriptionDataDTO.getSymptoms().size(); i++) {
                if (symText.length() > 0) {
                    symText.append(", ").append(generateFinalName(patientPrescriptionDataDTO.getSymptoms().get(i)));
                } else {
                    symText = new StringBuilder(generateFinalName(patientPrescriptionDataDTO.getSymptoms().get(i)));
                }
            }

            Chunk contentChunk = new Chunk(symText.toString(), fontDetails);
            paragraph.add(labelChunk);
            paragraph.add(contentChunk);
        }

        return paragraph;
    }

    public static String generateFinalName(Symptom symptom) {
        StringBuilder finalName = new StringBuilder(symptom.getSymptomName());

        if (symptom.getDuration() != null && !symptom.getDuration().isEmpty() && symptom.getDurationUnit() != null && !symptom.getDurationUnit().isEmpty()) {
            if (symptom.getSelectedCharacteristics() != null && !symptom.getSelectedCharacteristics().isEmpty()) {
                finalName.append(" (Since ").append(symptom.getDuration()).append(" ").append(symptom.getDurationUnit()).append(" with ").append(String.join(", ", symptom.getSelectedCharacteristics())).append(")");
            } else {
                finalName.append(" (Since ").append(symptom.getDuration()).append(" ").append(symptom.getDurationUnit()).append(")");
            }
        }

        if (symptom.getSelectedSeverity() != null && !symptom.getSelectedSeverity().isEmpty()) {
            finalName.append(" (").append(symptom.getSelectedSeverity()).append(")");
        }

        if (symptom.getNote() != null && !symptom.getNote().isEmpty()) {
            finalName.append(" (").append(symptom.getNote()).append(")");
        }

        if (symptom.getSelectedMoreOptions() != null && !symptom.getSelectedMoreOptions().isEmpty()) {
            boolean firstOption = true;
            finalName.append(" (");
            for (CmsMoreOptions moreOption : symptom.getSelectedMoreOptions()) {
                if (!firstOption) {
                    finalName.append(" | ");
                }
                finalName.append(moreOption.getTitle()).append(": ").append(String.join(", ", moreOption.getOptionDetails()));
                firstOption = false;
            }
            finalName.append(")");
        }

        return finalName.toString();
    }


    private Paragraph generateClinicalFindingsParagraph(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font fontDetails, Font lable) {
        Paragraph procedure = new Paragraph();
        Chunk labelChunk = new Chunk("Clinical Findings : ", lable);

        if (patientPrescriptionDataDTO.getClinicalFindings() != null && !patientPrescriptionDataDTO.getClinicalFindings().isEmpty()) {

            StringBuilder cfText = new StringBuilder();
            for (int i = 0; i < patientPrescriptionDataDTO.getClinicalFindings().size(); i++) {
                if (cfText.length() > 0) {
                    cfText.append(", ").append(getFinalCFName(patientPrescriptionDataDTO.getClinicalFindings().get(i)));
                } else {
                    cfText = new StringBuilder(getFinalCFName(patientPrescriptionDataDTO.getClinicalFindings().get(i)));
                }
            }

            Chunk cfContentChunk = new Chunk(cfText.toString(), fontDetails);


            procedure.add(labelChunk);
            procedure.add(cfContentChunk);
        }

        return procedure;
    }

    private static String getFinalCFName(ClinicalFinding cf) {
        //cf
        StringBuilder finalName = new StringBuilder(cf.getCfName());

        //time
        if (cf.getDuration() != null && !cf.getDuration().isEmpty() && cf.getDurationUnit() != null && !cf.getDurationUnit().isEmpty()) {
            if (cf.getSelectedCharacteristics() != null && !cf.getSelectedCharacteristics().isEmpty()) {
                finalName.append(" (Since ").append(cf.getDuration()).append(" ").append(cf.getDurationUnit()).append(" with ").append(String.join(", ", cf.getSelectedCharacteristics())).append(")");
            } else {
                finalName.append(" (Since ").append(cf.getDuration()).append(" ").append(cf.getDurationUnit()).append(")");
            }
        }

        if (cf.getSelectedSeverity() != null && !cf.getSelectedSeverity().isEmpty()) {
            finalName.append(" (").append(cf.getSelectedSeverity()).append(")");
        }


        //note
        if (cf.getNote() != null && !cf.getNote().isEmpty()) {
            finalName.append(" (").append(cf.getNote()).append(")");
        }

        //more options
        if (cf.getSelectedMoreOptions() != null && !cf.getSelectedMoreOptions().isEmpty()) {
            boolean firstOption = true;
            finalName.append(" (");
            for (CmsMoreOptions moreOption : cf.getSelectedMoreOptions()) {
                if (!firstOption) {
                    finalName.append(" | ");
                }
                finalName.append(moreOption.getTitle()).append(": ").append(String.join(", ", moreOption.getOptionDetails()));
                firstOption = false;
            }
            finalName.append(")");
        }

        return finalName.toString();
    }

    public Paragraph generateDiagnosisParagraph(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font fontDetails, Font lable) {
        Paragraph procedure = new Paragraph();
        Chunk labelChunk = new Chunk("Diagnosis : ", lable);

        if (patientPrescriptionDataDTO.getDiagnoses() != null && !patientPrescriptionDataDTO.getDiagnoses().isEmpty()) {
            StringBuilder dgText = new StringBuilder();
            for (int i = 0; i < patientPrescriptionDataDTO.getDiagnoses().size(); i++) {
                if (dgText.length() > 0) {
                    dgText.append(", ").append(getFinalDiagnosisName(patientPrescriptionDataDTO.getDiagnoses().get(i)));
                } else {
                    dgText = new StringBuilder(getFinalDiagnosisName(patientPrescriptionDataDTO.getDiagnoses().get(i)));
                }
            }

            Chunk dgContentChunk = new Chunk(dgText.toString(), fontDetails);

            procedure.add(labelChunk);
            procedure.add(dgContentChunk);
        }

        return procedure;
    }


    public static String getFinalDiagnosisName(Diagnosis diagnosis) {
        StringBuilder finalName = new StringBuilder(diagnosis.getDiagnosisName());

        if (diagnosis.getDiagnosisAction() != null && !diagnosis.getDiagnosisAction().isEmpty()) {
            finalName.append(" (").append(diagnosis.getDiagnosisAction()).append(")");
        }

        if (diagnosis.getNote() != null && !diagnosis.getNote().isEmpty()) {
            finalName.append(" (").append(diagnosis.getNote()).append(")");
        }

        if (diagnosis.getSelectedMoreOptions() != null && !diagnosis.getSelectedMoreOptions().isEmpty()) {
            boolean firstOption = true;
            finalName.append(" (");
            for (CmsMoreOptions moreOption : diagnosis.getSelectedMoreOptions()) {
                if (!firstOption) {
                    finalName.append(" | ");
                }
                finalName.append(moreOption.getTitle()).append(": ").append(String.join(", ", moreOption.getOptionDetails()));
                firstOption = false;
            }
            finalName.append(")");
        }

        return finalName.toString();
    }

    // add table logic
    public PdfPTable generateMedicineTable(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font tital, Font brandNameFont, Font fontGeneric, Font smallFont, PrescriptionSettings prescriptionSettings) throws DocumentException, IOException {
        PdfPTable table = null;
        if (patientPrescriptionDataDTO.getMedicineDTOS() != null && !patientPrescriptionDataDTO.getMedicineDTOS().isEmpty()) {
            String[] headers = new String[]{"Rx", "Brands", "Frequency", "Duration"};
            float[] columnWidths = new float[]{10f, 40f, 40f, 30f};

            if (prescriptionSettings.getTotalQuantity()) {
                headers = new String[]{"Rx", "Brands", "Frequency", "Duration", "Quantity"};
                columnWidths = new float[]{10f, 40f, 40f, 30f, 30f};
            }

            table = new PdfPTable(headers.length);

            for (String header : headers) {
                PdfPCell cell = new PdfPCell();
                cell.setPhrase(new Phrase(header.toUpperCase(), tital));
                table.addCell(cell);
            }

            table.setWidths(columnWidths);
            table.completeRow();

            int i = 1;
            for (MedicineDTO medicineDTO : patientPrescriptionDataDTO.getMedicineDTOS()) {
                PdfPCell cell1 = new PdfPCell();
                PdfPCell cell2 = new PdfPCell();
                PdfPCell cell5 = new PdfPCell();
                PdfPCell cell6 = new PdfPCell();

                cell1.setPhrase(new Phrase(String.valueOf(i), smallFont));
                table.addCell(cell1);

                Paragraph paragraph2 = new Paragraph();

                if (medicineDTO.getFormulatedBrandName() != null && !medicineDTO.getFormulatedBrandName().isEmpty())
                    paragraph2.add(new Phrase(medicineDTO.getFormulatedBrandName(), brandNameFont));

                if (prescriptionSettings.getMedicineComposition()) {
                    if (medicineDTO.getGenericName() != null && !medicineDTO.getGenericName().isEmpty()) {
                        Paragraph genericNameParagraph = new Paragraph();
                        if (medicineDTO.getFormulatedBrandName() != null && !medicineDTO.getFormulatedBrandName().isEmpty()) {
                            genericNameParagraph.add(Chunk.NEWLINE);
                            genericNameParagraph.add(Chunk.NEWLINE);
                        }
                        genericNameParagraph.add(new Phrase(medicineDTO.getGenericName().get(0).toUpperCase() + "\n\n", fontGeneric));

                        paragraph2.add(genericNameParagraph);
                    }
                }

                cell2.setPhrase(paragraph2);
                table.addCell(cell2);


                byte[] translatedFrequency = getFrequency(medicineDTO, patientPrescriptionDataDTO.getLanguageId()).getBytes(StandardCharsets.UTF_8);
                Phrase phrase3 = new Phrase(new String(translatedFrequency, StandardCharsets.UTF_8), smallFont);
                table.addCell(new PdfPCell(phrase3));

                Phrase phrase4 = new Phrase(
                        medicineDTO.getMedicineDosageList().stream()
                                .map(dosage -> translateDuration(patientPrescriptionDataDTO.getLanguageId(), dosage.getDuration()))
                                .filter(duration -> duration != null && !duration.isEmpty())
                                .collect(Collectors.joining("\n\n\n\n")), smallFont);
                table.addCell(new PdfPCell(phrase4));

                if (prescriptionSettings.getTotalQuantity()) {
                    String totalQuantity = medicineDTO.getMedicineDosageList()
                            .stream()
                            .map(MedicineDosage::getTotalQuantity)
                            .filter(Objects::nonNull)
                            .map(Integer::parseInt) // Assuming totalQuantity is a String representation of an integer
                            .map(quantity -> quantity == 0 ? "-" : String.valueOf(quantity))
                            .collect(Collectors.joining("\n\n\n\n"));

                    Phrase phrase5 = new Phrase(totalQuantity, smallFont);
                    cell5.setPhrase(phrase5);
                    table.addCell(cell5);
                }

                boolean isInstructions = false;
                StringJoiner joiner = new StringJoiner("\n");
                for (MedicineDosage m : medicineDTO.getMedicineDosageList()) {
                    if (m.getMedicineInstruction() != null && !m.getMedicineInstruction().isEmpty()) {
                        isInstructions = true;
                        List<String> medicineInstruction = m.getMedicineInstruction();
                        joiner.add(String.join(", ", medicineInstruction));
                    }
                }
                if (isInstructions) {
                    Phrase phrase6 = new Phrase(joiner.toString(), smallFont);
                    PdfPCell pdfPCell = new PdfPCell(phrase6);
                    pdfPCell.setColspan(headers.length);
                    cell6.setPhrase(phrase6);
                    cell6.setColspan(headers.length);
                    table.addCell(cell6);
                }
                table.completeRow();
                i++;
            }
        }
        return table;
    }

    //add investigation
    private Paragraph generateInvestigationsParagraph(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font tital, Font fontDetails) {
        Paragraph procedure3 = new Paragraph();
        Chunk investigationPhrase = new Chunk("Investigations : ", tital);

        Chunk inContentChunk = getChunk(patientPrescriptionDataDTO, fontDetails);

        procedure3.add(investigationPhrase);
        procedure3.add(inContentChunk);


        return procedure3;
    }

    private static Chunk getChunk(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font fontDetails) {
        StringBuilder investigationTxt = new StringBuilder();

        List<Investigation> investigationList = patientPrescriptionDataDTO.getInvestigations();
        int lastIndex = investigationList.size() - 1;

        for (int i = 0; i < investigationList.size(); i++) {
            Investigation investigation = investigationList.get(i);
            if (investigation.getInvestigationName() != null) {
                investigationTxt.append(investigation.getInvestigationName());
                if (i < lastIndex) {
                    investigationTxt.append(", ");
                }
            }
        }

        return new Chunk(investigationTxt.toString(), fontDetails);
    }

    // Modify your addMedicalInfo method to ensure proper spacing between sections
    private Paragraph addMedicalInfo(String familyMemberId, Font title, Font details) {
        Gynec gynec = gynecRepository.findByFamilyMemberId(familyMemberId);

        if (gynec == null) return null;

        Paragraph medicalInfo = new Paragraph();
        medicalInfo.setAlignment(Element.ALIGN_LEFT);


        Chunk gtpalHeaderChunk = new Chunk("GTPAL : ", title);
        medicalInfo.add(gtpalHeaderChunk);
        if (gynec.getGpal() != null) {
            StringBuilder gtpalContent = new StringBuilder();
            gtpalContent.append("G");
            gtpalContent.append(gynec.getGpal().getGravida());
            gtpalContent.append(" T");
            gtpalContent.append(gynec.getGpal().getTerm());
            gtpalContent.append(" P");
            gtpalContent.append(gynec.getGpal().getPreterm());
            gtpalContent.append(" A");
            gtpalContent.append(gynec.getGpal().getAbortions());
            gtpalContent.append(" L");
            gtpalContent.append(gynec.getGpal().getLivingChildren());
            medicalInfo.add(new Chunk(gtpalContent.toString(), details));
        } else {
            medicalInfo.add(new Chunk(" NA", details));
        }

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        StringBuilder lmpEdd = new StringBuilder();
        lmpEdd.append(gynec.getLmp() != null ? gynec.getLmp().toString("dd-MMM-yyyy") : "NA");
        lmpEdd.append("/");
        lmpEdd.append(gynec.getEdd() != null ? gynec.getEdd().toString("dd-MMM-yyyy") : "NA");
        Chunk lmpEddCunk = new Chunk("LMP/EDD : ", title);
        Chunk dateLMPEDD = new Chunk(lmpEdd.toString(), details);
        medicalInfo.add(lmpEddCunk);
        medicalInfo.add(dateLMPEDD);

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        medicalInfo.add(new Chunk("C-EDD : ", title));
        medicalInfo.add(new Chunk(gynec.getCEdd() != null ? gynec.getCEdd().toString("dd-MMM-yyyy") : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding cycle
        medicalInfo.add(new Chunk("Cycle : ", title));
        medicalInfo.add(new Chunk(gynec.getCycle() != null ? String.format("%s %s", gynec.getCycle().getType(), "for " + gynec.getCycle().getDays() + " days") : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding flow
        medicalInfo.add(new Chunk("Flow : ", title));
        medicalInfo.add(new Chunk(gynec.getFlow() != null ? String.format("%s %s", gynec.getFlow().getType(), "for " + gynec.getFlow().getDays() + " days") : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding surgery
        medicalInfo.add(new Chunk("Surgery : ", title));
        medicalInfo.add(new Chunk(gynec.getSurgeryList() != null && !gynec.getSurgeryList().isEmpty() ? genreateSurgeryContent(gynec.getSurgeryList()) : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        medicalInfo.add(new Chunk("Patient Medical Disease : ", title));
        medicalInfo.add(new Chunk(gynec.getPatientHistory() != null && !gynec.getPatientHistory().isEmpty() ? genreateMedicalDiseaseContent(gynec.getPatientHistory()) : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding family member medical disease
        medicalInfo.add(new Chunk("Family Member Medical Disease : ", title));
        medicalInfo.add(new Chunk(gynec.getFamilyMemberHistory() != null && !gynec.getFamilyMemberHistory().isEmpty() ? genreateMedicalDiseaseContent(gynec.getFamilyMemberHistory()) : " NA", details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding breast cancer details
        medicalInfo.add(new Chunk("Breast Cancer : ", title));
        medicalInfo.add(new Chunk(generateBreastCancerContent(gynec.getBreastCancer()), details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding content of Child with mental or genetic disorder
        medicalInfo.add(new Chunk("Child with mental or genetic disorder : ", title));
        medicalInfo.add(new Chunk(generateChildWithMentalOrGeneticDisorderContent(gynec.getChildWithMentalOrGeneticDisorder()), details));

        // want to start from new line

        medicalInfo.add(addSpaceMedicalInfo());

        // adding content of Menopause age
        medicalInfo.add(new Chunk("Menopause Age : ", title));
        medicalInfo.add(new Chunk(genreateMenoPauseAge(gynec.getMenopausheAge()), details));

        return medicalInfo;
    }

    // Add space
    private Paragraph addSpaceMedicalInfo() {
        Paragraph spaceParagraph = new Paragraph();
        spaceParagraph.setSpacingBefore(1f); // Adjust the spacing before as needed
        return spaceParagraph;
    }

    private String genreateMenoPauseAge(MenopausheAge menopausheAge) {
        if (menopausheAge == null) {
            return " NA";
        } else {
            StringBuilder menoPauseAgeContent = new StringBuilder();
            String age = menopausheAge.getAge() != null ? menopausheAge.getAge().toString() : "0";
            menoPauseAgeContent.append(age);

            String note = menopausheAge.getNote() != null ? menopausheAge.getNote() : "";
            menoPauseAgeContent.append(note);

            return menoPauseAgeContent.toString();
        }
    }


    private String genreateSurgeryContent(List<Surgery> surgeries) {
        return surgeries.stream().map(Surgery::getName)
                .collect(Collectors.joining(" | "));
    }

    private String genreateMedicalDiseaseContent(List<Diagnosis> diagnoses) {
        return diagnoses.stream().map(Diagnosis::getDiagnosisName)
                .collect(Collectors.joining(" | "));
    }

    private String generateBreastCancerContent(BreastCancer breastCancer) {
        if (breastCancer == null) {
            return " NA";
        } else {
            StringBuilder breastCancerContentString = new StringBuilder();
            breastCancerContentString.append(breastCancer.getBreastCancer() != null && breastCancer.getBreastCancer() ? " Yes " : " No ");
            breastCancerContentString.append(" (");
            breastCancerContentString.append("Note : ");
            breastCancerContentString.append(breastCancer.getNote() != null ? breastCancer.getNote() : "");
            breastCancerContentString.append(")");
            return breastCancerContentString.toString();
        }
    }


    private String generateChildWithMentalOrGeneticDisorderContent(ChildWithMentalOrGeneticDisorder childWithMentalOrGeneticDisorder) {
        if (childWithMentalOrGeneticDisorder == null) {
            return " NA";
        } else {
            StringBuilder childWithMentalOrGeneticDisorderContent = new StringBuilder();
            String yesNo = childWithMentalOrGeneticDisorder.getChildWithMentalOrGeneticDisorder() != null && childWithMentalOrGeneticDisorder.getChildWithMentalOrGeneticDisorder() ? " Yes " : " NO ";
            childWithMentalOrGeneticDisorderContent.append(yesNo);
            childWithMentalOrGeneticDisorderContent.append(" (");
            childWithMentalOrGeneticDisorderContent.append("Note : ");
            String content = childWithMentalOrGeneticDisorder.getNote() != null ? childWithMentalOrGeneticDisorder.getNote() : "";
            childWithMentalOrGeneticDisorderContent.append(content).append(")");
            return childWithMentalOrGeneticDisorderContent.toString();
        }
    }


    //add generalInstruction
    public Paragraph generateGeneralInstructionsParagraph(PatientPrescriptionDataDTO patientPrescriptionDataDTO, Font tital, Font instructionFont) {
        Paragraph procedure4 = new Paragraph();
        Chunk instructionsPhrase = new Chunk("General instructions : ", tital);

        if (patientPrescriptionDataDTO.getInstructions() != null && !patientPrescriptionDataDTO.getInstructions().isEmpty()) {
            com.itextpdf.text.List instructionsList = new com.itextpdf.text.List();
            instructionsList.setIndentationLeft(60);
            instructionsList.setIndentationRight(36);
            StringBuilder instructionText = new StringBuilder();

            List<Instruction> instructionList = patientPrescriptionDataDTO.getInstructions();
            if (instructionList != null && !instructionList.isEmpty()) {
                for (int i = 0; i < instructionList.size(); i++) {
                    Instruction instruction = instructionList.get(i);
                    if (instruction.getInstructions() != null) {
                        if (i > 0) instructionText.append("\n");
                        instructionText.append("- ");
                        instructionText.append(getInstructions(instruction, patientPrescriptionDataDTO.getLanguageId()));
                    }
                }
                Paragraph instructionContentParagraph = new Paragraph(instructionText.toString(), instructionFont);
                procedure4.add(instructionsPhrase);
                procedure4.add(Chunk.NEWLINE);
                procedure4.add(instructionContentParagraph);
            }
        }

        return procedure4;
    }

    // adding app name

    private static Paragraph createAppNameParagraph(String appName, Font blueFont) {
        String appNameText = (appName != null) ? appName : "Q UP";
        String content = "Download your prescription on " + appNameText + " app";
        Paragraph appNameParagraph = new Paragraph(content, blueFont);
        appNameParagraph.setSpacingAfter(5f);
        appNameParagraph.setAlignment(Element.ALIGN_LEFT);
        return appNameParagraph;
    }

    //QR code generator
    private Image createQRCodeImage(String downloadLink) throws Exception {
        String link = (downloadLink != null) ? downloadLink : QUP_APP_DOWNLOAD_LINK;
        BitMatrix qr = generateQRCodeImage(link);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(qr);
        Image qrImage = Image.getInstance(bufferedImage, null);
        qrImage.scaleAbsolute(100, 100);
        qrImage.setAlignment(Element.ALIGN_LEFT);
        return qrImage;
    }

    private BitMatrix generateQRCodeImage(String data) throws WriterException {
        int width = 100;
        int height = 100;

        Map<com.google.zxing.EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        return new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, width, height, hints);
    }

    private void applyFontConfiguration(PrescriptionPrintSettings prescriptionPrintSettings, Font fontTitle, Font fontDetails, Font fontHeader, Font fontGeneric, Font natoSans, Font smallFont) {
        PrescriptionFontConfiguration fontConfiguration = prescriptionPrintSettings.getPrescriptionFontConfiguration();
        if (fontConfiguration != null) {
            Integer fontDetailsSize = fontConfiguration.getFontDetails();
            Integer fontTitleSize = fontConfiguration.getFontTitle();
            Integer fontGenericSize = fontConfiguration.getFontGeneric();
            Integer fontHeaderSize = fontConfiguration.getFontHeader();

            if (fontDetailsSize != null && fontDetailsSize >= 8 && fontDetailsSize <= 14) {
                fontDetails.setSize(fontDetailsSize);
                natoSans.setSize(fontDetailsSize);
                smallFont.setSize(fontDetailsSize);

            }

            if (fontTitleSize != null && fontTitleSize >= 8 && fontTitleSize <= 14) {
                fontTitle.setSize(fontTitleSize);
            }

            if (fontGenericSize != null && fontGenericSize >= 8 && fontGenericSize <= 14) {
                fontGeneric.setSize(fontGenericSize);
            }

            if (fontHeaderSize != null && fontHeaderSize >= 8 && fontHeaderSize <= 14) {
                fontHeader.setSize(fontHeaderSize);
            }
        }
    }

    // add space
    private Chunk addSpace() {
        return new Chunk("\n");
    }

}


