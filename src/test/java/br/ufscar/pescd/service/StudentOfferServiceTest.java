package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.DocumentationForm;
import br.ufscar.pescd.dto.ReportForm;
import br.ufscar.pescd.dto.StudentEnrollmentDetailsResponse;
import br.ufscar.pescd.dto.StudentOfferSummaryResponse;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.dto.WorkPlanForm;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.DocumentationRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentOfferServiceTest {

    private static final Long ENROLLMENT_ID = 100L;
    private static final Long SUPERVISOR_ID = 10L;
    private static final String USERNAME = "jlima";
    private static final int FIVE_MB = 5 * 1024 * 1024;

    @Mock
    private OfferStudentRepository offerStudentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkPlanRepository workPlanRepository;

    @Mock
    private DocumentationRepository documentationRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private StatusChangeLogRepository statusChangeLogRepository;

    @Mock
    private OfferApiMapper offerApiMapper;

    @Mock
    private SeaweedFsStorageService storageService;

    @InjectMocks
    private StudentOfferService service;

    private User student;
    private User professor;
    private OfferStudent enrollment;

    @BeforeEach
    void setUp() {
        student = user(2L, USERNAME, UserRole.ALUNO);
        professor = user(SUPERVISOR_ID, "alevada", UserRole.PROFESSOR);

        Offer offer = new Offer();
        offer.setId(1L);
        offer.setStatus(OfferStatus.EM_ANDAMENTO);

        enrollment = new OfferStudent();
        enrollment.setId(ENROLLMENT_ID);
        enrollment.setOffer(offer);
        enrollment.setStudent(student);
        enrollment.setStatus(StudentOfferStatus.NAO_ENVIADO);
    }

    // AL.01

    @Test
    void listaAsInscricoesDoAlunoLogado() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(student));
        when(offerStudentRepository.findByStudent(student)).thenReturn(List.of(enrollment));

        assertThat(service.findStudentEnrollments(USERNAME)).containsExactly(enrollment);
    }

    @Test
    void listarInscricoesDeAlunoInexistenteLancaExcecao() {
        when(userRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findStudentEnrollments("fantasma"));

        assertThat(ex.getMessage()).isEqualTo("Aluno não encontrado.");
    }

    @Test
    void listaDaApiConverteCadaInscricaoComOMapper() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(student));
        when(offerStudentRepository.findByStudent(student)).thenReturn(List.of(enrollment));
        StudentOfferSummaryResponse response = mock(StudentOfferSummaryResponse.class);
        when(offerApiMapper.toStudentOfferSummary(enrollment)).thenReturn(response);

        assertThat(service.listEnrollmentsForApi(USERNAME)).containsExactly(response);
    }

    @Test
    void buscaInscricaoQuePertenceAoAluno() {
        givenEnrollmentExists();

        assertThat(service.getEnrollment(ENROLLMENT_ID, USERNAME)).isSameAs(enrollment);
    }

    @Test
    void buscarInscricaoInexistenteLancaExcecao() {
        when(offerStudentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollment(ENROLLMENT_ID, USERNAME));

        assertThat(ex.getMessage()).isEqualTo("Inscrição não encontrada.");
    }

    @Test
    void buscarInscricaoDeOutroAlunoLancaExcecao() {
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollment(ENROLLMENT_ID, "msouza"));

        assertThat(ex.getMessage()).isEqualTo("Esta inscrição não pertence ao aluno logado.");
    }

    @Test
    void detalhesDaInscricaoNaApiUsamOMapper() {
        givenEnrollmentExists();
        StudentEnrollmentDetailsResponse response = mock(StudentEnrollmentDetailsResponse.class);
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(response);

        assertThat(service.getEnrollmentDetailsForApi(ENROLLMENT_ID, USERNAME)).isSameAs(response);
    }

    @Test
    void listaSomenteUsuariosComPerfilProfessor() {
        when(userRepository.findByRole(UserRole.PROFESSOR)).thenReturn(List.of(professor));

        assertThat(service.listProfessors()).containsExactly(professor);
    }

    @Test
    void listaDeProfessoresDaApiUsaOMapper() {
        when(userRepository.findByRole(UserRole.PROFESSOR)).thenReturn(List.of(professor));
        UserSummaryResponse response = mock(UserSummaryResponse.class);
        when(offerApiMapper.toUserSummary(professor)).thenReturn(response);

        assertThat(service.listProfessorsForApi()).containsExactly(response);
    }

    @Test
    void retornaOsDocumentosEnviadosDaInscricao() {
        WorkPlan plan = new WorkPlan();
        Documentation documentation = new Documentation();
        Report report = new Report();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plan));
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(documentation));
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(report));

        assertThat(service.getWorkPlan(enrollment)).isSameAs(plan);
        assertThat(service.getDocumentation(enrollment)).isSameAs(documentation);
        assertThat(service.getReport(enrollment)).isSameAs(report);
    }

    @Test
    void retornaNuloQuandoNenhumDocumentoFoiEnviado() {
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());

        assertThat(service.getWorkPlan(enrollment)).isNull();
        assertThat(service.getDocumentation(enrollment)).isNull();
        assertThat(service.getReport(enrollment)).isNull();
    }

    @Test
    void retornaOHistoricoDeStatusDaInscricao() {
        StatusChangeLog log = new StatusChangeLog();
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(enrollment)).thenReturn(List.of(log));

        assertThat(service.getStatusLogs(enrollment)).containsExactly(log);
    }

    // AL.02

    @Test
    void enviarPlanoSalvaOsDadosOArquivoEMudaStatusParaPlanoEnviado() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), anyString(), anyString())).thenReturn("3,abc");
        MultipartFile file = pdf("plano.pdf", 10);

        service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(file));

        ArgumentCaptor<WorkPlan> captor = ArgumentCaptor.forClass(WorkPlan.class);
        verify(workPlanRepository).save(captor.capture());
        WorkPlan saved = captor.getValue();
        assertThat(saved.getOfferStudent()).isSameAs(enrollment);
        assertThat(saved.getDisciplineCode()).isEqualTo("1001");
        assertThat(saved.getDisciplineName()).isEqualTo("Algoritmos");
        assertThat(saved.getDisciplineCourse()).isEqualTo("Computação");
        assertThat(saved.getFileName()).isEqualTo("plano.pdf");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFileFid()).isEqualTo("3,abc");
        verify(storageService).store(new byte[10], "plano.pdf", "application/pdf");
        assertThat(enrollment.getSupervisor()).isSameAs(professor);
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_ENVIADO);
        verify(offerStudentRepository).save(enrollment);
        assertStatusLogged(StudentOfferStatus.NAO_ENVIADO, StudentOfferStatus.PLANO_ENVIADO,
                "Plano de trabalho enviado");
    }

    @Test
    void reenviarPlanoAtualizaORegistroExistenteEApagaOArquivoAntigo() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));
        WorkPlan existing = new WorkPlan();
        existing.setFileFid("1,antigo");
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existing));
        when(storageService.store(any(), anyString(), anyString())).thenReturn("3,novo");

        service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10)));

        verify(storageService).delete("1,antigo");
        verify(workPlanRepository).save(existing);
        assertThat(existing.getFileFid()).isEqualTo("3,novo");
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = "EM_ANDAMENTO", mode = EnumSource.Mode.EXCLUDE)
    void enviarPlanoEmOfertaQueNaoEstaEmAndamentoLancaExcecao(OfferStatus status) {
        enrollment.getOffer().setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10))));

        assertThat(ex.getMessage()).isEqualTo("Esta ação só é permitida em ofertas em andamento.");
        verify(workPlanRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = StudentOfferStatus.class, names = "NAO_ENVIADO", mode = EnumSource.Mode.EXCLUDE)
    void enviarPlanoComStatusDiferenteDeNaoEnviadoLancaExcecao(StudentOfferStatus status) {
        enrollment.setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10))));

        assertThat(ex.getMessage())
                .isEqualTo("Ação indisponível: o status atual é \"" + status.getDisplayName() + "\".");
        verify(workPlanRepository, never()).save(any());
    }

    @Test
    void enviarPlanoComSupervisorInexistenteLancaExcecao() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10))));

        assertThat(ex.getMessage()).isEqualTo("Professor supervisor inválido.");
    }

    @Test
    void enviarPlanoComSupervisorQueNaoEProfessorLancaExcecao() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(user(SUPERVISOR_ID, "msouza", UserRole.ALUNO)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10))));

        assertThat(ex.getMessage()).isEqualTo("Professor supervisor inválido.");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.NAO_ENVIADO);
    }

    @Test
    void enviarPlanoSemArquivoLancaExcecao() {
        assertInvalidPlanFile(null, "O arquivo é obrigatório.");
    }

    @Test
    void enviarPlanoComArquivoVazioLancaExcecao() {
        assertInvalidPlanFile(pdf("plano.pdf", 0), "O arquivo é obrigatório.");
    }

    @Test
    void enviarPlanoComArquivoAcimaDe5MbLancaExcecao() {
        assertInvalidPlanFile(pdf("plano.pdf", FIVE_MB + 1), "O arquivo deve ter no máximo 5 MB.");
    }

    @Test
    void enviarPlanoComArquivoQueNaoEPdfLancaExcecao() {
        MultipartFile file = new MockMultipartFile("file", "plano.docx", "application/msword", new byte[10]);

        assertInvalidPlanFile(file, "O arquivo deve estar no formato PDF.");
    }

    @Test
    void enviarPlanoComArquivoSemNomeEQueNaoEPdfLancaExcecao() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getOriginalFilename()).thenReturn(null);

        assertInvalidPlanFile(file, "O arquivo deve estar no formato PDF.");
    }

    @Test
    void enviarPlanoComFalhaAoLerOArquivoLancaExcecao() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getOriginalFilename()).thenReturn("plano.pdf");
        when(file.getBytes()).thenThrow(new IOException("disco"));

        assertInvalidPlanFile(file, "Não foi possível ler o arquivo enviado.");
    }

    @Test
    void enviarPlanoComArquivoDeExatamente5MbEAceito() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());

        service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", FIVE_MB)));

        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_ENVIADO);
    }

    @Test
    void enviarPlanoAceitaPdfIdentificadoSoPelaExtensao() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "PLANO.PDF", "application/octet-stream", new byte[10]);

        service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(file));

        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_ENVIADO);
    }

    @Test
    void enviarPlanoPelaApiRetornaOsDetalhesAtualizados() {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        StudentEnrollmentDetailsResponse response = mock(StudentEnrollmentDetailsResponse.class);
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(response);

        assertThat(service.submitWorkPlanForApi(ENROLLMENT_ID, USERNAME, workPlanForm(pdf("plano.pdf", 10))))
                .isSameAs(response);
    }

    // AL.03

    @Test
    void enviarDocumentacaoSalvaOsDadosEMudaStatusParaDocumentacaoEnviada() {
        givenEnrollmentExists();
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), anyString(), anyString())).thenReturn("4,doc");

        service.submitDocumentation(ENROLLMENT_ID, USERNAME, documentationForm(pdf("doc.pdf", 10)));

        ArgumentCaptor<Documentation> captor = ArgumentCaptor.forClass(Documentation.class);
        verify(documentationRepository).save(captor.capture());
        Documentation saved = captor.getValue();
        assertThat(saved.getOfferStudent()).isSameAs(enrollment);
        assertThat(saved.getInstitutionName()).isEqualTo("UFSCar");
        assertThat(saved.getDisciplineName()).isEqualTo("Cálculo 1");
        assertThat(saved.getDisciplineCourse()).isEqualTo("Engenharia");
        assertThat(saved.getWorkloadHours()).isEqualTo(60);
        assertThat(saved.getFileName()).isEqualTo("doc.pdf");
        assertThat(saved.getFileFid()).isEqualTo("4,doc");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        assertStatusLogged(StudentOfferStatus.NAO_ENVIADO, StudentOfferStatus.DOCUMENTACAO_ENVIADA,
                "Documentação enviada");
    }

    @Test
    void reenviarDocumentacaoAtualizaORegistroExistenteEApagaOArquivoAntigo() {
        givenEnrollmentExists();
        Documentation existing = new Documentation();
        existing.setFileFid("1,antigo");
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existing));

        service.submitDocumentation(ENROLLMENT_ID, USERNAME, documentationForm(pdf("doc.pdf", 10)));

        verify(storageService).delete("1,antigo");
        verify(documentationRepository).save(existing);
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = "EM_ANDAMENTO", mode = EnumSource.Mode.EXCLUDE)
    void enviarDocumentacaoEmOfertaQueNaoEstaEmAndamentoLancaExcecao(OfferStatus status) {
        enrollment.getOffer().setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(ENROLLMENT_ID, USERNAME, documentationForm(pdf("doc.pdf", 10))));

        assertThat(ex.getMessage()).isEqualTo("Esta ação só é permitida em ofertas em andamento.");
    }

    @ParameterizedTest
    @EnumSource(value = StudentOfferStatus.class, names = "NAO_ENVIADO", mode = EnumSource.Mode.EXCLUDE)
    void enviarDocumentacaoComStatusDiferenteDeNaoEnviadoLancaExcecao(StudentOfferStatus status) {
        enrollment.setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(ENROLLMENT_ID, USERNAME, documentationForm(pdf("doc.pdf", 10))));

        assertThat(ex.getMessage())
                .isEqualTo("Ação indisponível: o status atual é \"" + status.getDisplayName() + "\".");
        verify(documentationRepository, never()).save(any());
    }

    @Test
    void enviarDocumentacaoComArquivoAcimaDe5MbLancaExcecao() {
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(ENROLLMENT_ID, USERNAME,
                        documentationForm(pdf("doc.pdf", FIVE_MB + 1))));

        assertThat(ex.getMessage()).isEqualTo("O arquivo deve ter no máximo 5 MB.");
        verify(documentationRepository, never()).save(any());
    }

    @Test
    void enviarDocumentacaoPelaApiRetornaOsDetalhesAtualizados() {
        givenEnrollmentExists();
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        StudentEnrollmentDetailsResponse response = mock(StudentEnrollmentDetailsResponse.class);
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(response);

        assertThat(service.submitDocumentationForApi(ENROLLMENT_ID, USERNAME, documentationForm(pdf("doc.pdf", 10))))
                .isSameAs(response);
    }

    // AL.04

    @Test
    void enviarRelatorioSalvaAFrequenciaOArquivoEMudaStatusParaRelatorioEnviado() {
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        givenEnrollmentExists();
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), anyString(), anyString())).thenReturn("5,rel");

        service.submitReport(ENROLLMENT_ID, USERNAME, reportForm(85, pdf("relatorio.pdf", 10)));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getOfferStudent()).isSameAs(enrollment);
        assertThat(saved.getFrequency()).isEqualTo(85);
        assertThat(saved.getFileName()).isEqualTo("relatorio.pdf");
        assertThat(saved.getFileFid()).isEqualTo("5,rel");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.RELATORIO_ENVIADO);
        assertStatusLogged(StudentOfferStatus.PLANO_APROVADO, StudentOfferStatus.RELATORIO_ENVIADO,
                "Relatório final enviado");
    }

    @Test
    void reenviarRelatorioAtualizaORegistroExistenteEApagaOArquivoAntigo() {
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        givenEnrollmentExists();
        Report existing = new Report();
        existing.setFileFid("1,antigo");
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existing));

        service.submitReport(ENROLLMENT_ID, USERNAME, reportForm(100, pdf("relatorio.pdf", 10)));

        verify(storageService).delete("1,antigo");
        verify(reportRepository).save(existing);
        assertThat(existing.getFrequency()).isEqualTo(100);
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = "EM_ANDAMENTO", mode = EnumSource.Mode.EXCLUDE)
    void enviarRelatorioEmOfertaQueNaoEstaEmAndamentoLancaExcecao(OfferStatus status) {
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        enrollment.getOffer().setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(ENROLLMENT_ID, USERNAME, reportForm(85, pdf("relatorio.pdf", 10))));

        assertThat(ex.getMessage()).isEqualTo("Esta ação só é permitida em ofertas em andamento.");
    }

    @ParameterizedTest
    @EnumSource(value = StudentOfferStatus.class, names = "PLANO_APROVADO", mode = EnumSource.Mode.EXCLUDE)
    void enviarRelatorioComStatusDiferenteDePlanoAprovadoLancaExcecao(StudentOfferStatus status) {
        enrollment.setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(ENROLLMENT_ID, USERNAME, reportForm(85, pdf("relatorio.pdf", 10))));

        assertThat(ex.getMessage())
                .isEqualTo("Ação indisponível: o status atual é \"" + status.getDisplayName() + "\".");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void enviarRelatorioComArquivoQueNaoEPdfLancaExcecao() {
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        givenEnrollmentExists();
        MultipartFile file = new MockMultipartFile("file", "relatorio.txt", "text/plain", new byte[10]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(ENROLLMENT_ID, USERNAME, reportForm(85, file)));

        assertThat(ex.getMessage()).isEqualTo("O arquivo deve estar no formato PDF.");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void enviarRelatorioPelaApiRetornaOsDetalhesAtualizados() {
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        givenEnrollmentExists();
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        StudentEnrollmentDetailsResponse response = mock(StudentEnrollmentDetailsResponse.class);
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(response);

        assertThat(service.submitReportForApi(ENROLLMENT_ID, USERNAME, reportForm(85, pdf("relatorio.pdf", 10))))
                .isSameAs(response);
    }

    private void givenEnrollmentExists() {
        when(offerStudentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    }

    private void assertInvalidPlanFile(MultipartFile file, String message) {
        givenEnrollmentExists();
        when(userRepository.findById(SUPERVISOR_ID)).thenReturn(Optional.of(professor));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(ENROLLMENT_ID, USERNAME, workPlanForm(file)));

        assertThat(ex.getMessage()).isEqualTo(message);
        verify(storageService, never()).store(any(), any(), any());
        verify(workPlanRepository, never()).save(any());
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.NAO_ENVIADO);
    }

    private void assertStatusLogged(StudentOfferStatus previous, StudentOfferStatus next, String description) {
        ArgumentCaptor<StatusChangeLog> captor = ArgumentCaptor.forClass(StatusChangeLog.class);
        verify(statusChangeLogRepository).save(captor.capture());
        StatusChangeLog log = captor.getValue();
        assertThat(log.getOfferStudent()).isSameAs(enrollment);
        assertThat(log.getPreviousStatus()).isEqualTo(previous);
        assertThat(log.getNewStatus()).isEqualTo(next);
        assertThat(log.getDescription()).isEqualTo(description);
    }

    private static MultipartFile pdf(String name, int size) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[size]);
    }

    private static WorkPlanForm workPlanForm(MultipartFile file) {
        WorkPlanForm form = new WorkPlanForm();
        form.setDisciplineCode("1001");
        form.setDisciplineName("Algoritmos");
        form.setDisciplineCourse("Computação");
        form.setSupervisorId(SUPERVISOR_ID);
        form.setFile(file);
        return form;
    }

    private static DocumentationForm documentationForm(MultipartFile file) {
        DocumentationForm form = new DocumentationForm();
        form.setInstitutionName("UFSCar");
        form.setDisciplineName("Cálculo 1");
        form.setDisciplineCourse("Engenharia");
        form.setWorkloadHours(60);
        form.setFile(file);
        return form;
    }

    private static ReportForm reportForm(int frequency, MultipartFile file) {
        ReportForm form = new ReportForm();
        form.setFrequency(frequency);
        form.setFile(file);
        return form;
    }

    private static User user(Long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
