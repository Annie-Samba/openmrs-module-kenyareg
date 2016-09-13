package org.openmrs.module.kenyareg.api;

import ke.go.moh.oec.Person;
import ke.go.moh.oec.Person.Sex;
import ke.go.moh.oec.PersonIdentifier;
import ke.go.moh.oec.PersonIdentifier.Type;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonName;
import org.openmrs.api.PatientService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class PersonMergeServiceTest {

	@Mock PatientService patientServiceMock;
	@InjectMocks PersonMergeService mergeService;

	@Before public void setup() {
		Mockito.when(patientServiceMock.getAllPatientIdentifierTypes()).thenReturn(getMockListOfIdentifiers());
	}

	@Test public void mergePatientIdentifiers_shouldAddPatientIdentifierIfNew() throws ParseException {
		Person person = getMpiPerson("New ID", Type.cccLocalId);
		Patient omrsPatient = new Patient();

		Assert.assertThat(omrsPatient.getIdentifiers().size(), Matchers.equalTo(0));

		mergeService.mergePatientIdentifiers(omrsPatient, person.getPersonIdentifierList(), null);

		Assert.assertThat(omrsPatient.getIdentifiers().size(), Matchers.equalTo(1));
	}

	@Test public void mergePatientIdentifiers_shouldUpdateExistingPatientIdenfier() throws ParseException {
		PatientIdentifierType identifierType = getCccLocalIdentifier();
		Mockito.when(patientServiceMock.getPatientIdentifierTypeByName(Mockito.contains(Type.cccLocalId.toString()))).thenReturn(identifierType);
		Person person = getMpiPerson("New ID", Type.cccLocalId);
		Patient omrsPatient = new Patient();
		PatientIdentifier identifier = new PatientIdentifier("ID 2", identifierType, null);
		omrsPatient.addIdentifier(identifier);

		mergeService.mergePatientIdentifiers(omrsPatient, person.getPersonIdentifierList(), null);

		Assert.assertThat(omrsPatient.getIdentifiers().size(), Matchers.equalTo(1));
		Assert.assertThat(omrsPatient.getIdentifiers(), Matchers.hasItem(Matchers.<PatientIdentifier>hasProperty("identifier", Matchers.equalToIgnoringCase("New ID"))));
	}

	@Test public void mergePatientIdentifiers_shouldNotUpdateIdentifierIfIdentical() throws ParseException {
		PatientIdentifierType identifierType = getCccLocalIdentifier();
		Mockito.when(patientServiceMock.getPatientIdentifierTypeByName(Mockito.contains("TYPE 1"))).thenReturn(identifierType);
		Patient omrsPatient = new Patient();
		Person mpiPerson = getMpiPerson("ID 2", Type.cccLocalId);
		PatientIdentifier identifier = new PatientIdentifier("ID 2", identifierType, null);
		omrsPatient.addIdentifier(identifier);

		mergeService.mergePatientIdentifiers(omrsPatient, mpiPerson.getPersonIdentifierList(), null);

		Assert.assertThat(omrsPatient.getIdentifiers().size(), Matchers.equalTo(1));
		Assert.assertThat(omrsPatient.getIdentifiers(), Matchers.hasItem(Matchers.<PatientIdentifier>hasProperty("identifier", Matchers.equalToIgnoringCase("ID 2"))));
	}

	@Test public void mergePerson_shouldCreateOmrsPersonWithMpiPersonDetails() throws ParseException {
		Person mpiPerson = getMpiPerson("NEWID", Type.cccLocalId);

		org.openmrs.Person omrsPerson = mergeService.mergePerson(null, mpiPerson);

		Assert.assertThat(omrsPerson.getPersonName().getGivenName(), Matchers.is(mpiPerson.getFirstName()));
		Assert.assertThat(omrsPerson.getBirthdate(), Matchers.is(mpiPerson.getBirthdate()));
		Assert.assertThat(omrsPerson.getGender(), Matchers.is(mpiPerson.getSex().toString()));
		Assert.assertThat(omrsPerson.getUuid(), Matchers.is(mpiPerson.getPersonGuid()));
	}

	@Test public void mergePerson_shouldUpdateOmrsPersonWithMpiPersonDetails() throws ParseException {
		Person mpiPerson = getMpiPerson("NEWID", Type.cccLocalId);
		org.openmrs.Person omrsPerson = new org.openmrs.Person();
		PersonName personName = new PersonName();
		personName.setGivenName("John");
		personName.setFamilyName("The II");
		omrsPerson.addName(personName);
		omrsPerson.setUuid(mpiPerson.getPersonGuid());

		omrsPerson = mergeService.mergePerson(null, mpiPerson);

		Assert.assertThat(omrsPerson.getPersonName().getGivenName(), Matchers.is(mpiPerson.getFirstName()));
		Assert.assertThat(omrsPerson.getPersonName().getFamilyName(), Matchers.is(mpiPerson.getLastName()));
	}

	@Test public void getLpiMpiMergedProperties_shouldPreferNonEmptyProperties() {
		Person lpiPerson = new Person();
		lpiPerson.setFirstName("FirstName");
		Date birthDate = new Date();
		lpiPerson.setBirthdate(birthDate);
		Person mpiPerson = new Person();
		Map<String, Object> mergedProperties = mergeService.getLpiMpiMergedProperties(lpiPerson, mpiPerson);

		Assert.assertThat(mergedProperties.size(), Matchers.is(2));
		Assert.assertThat(mergedProperties.get("firstName").toString(), Matchers.equalTo("FirstName"));
		Assert.assertThat((Date)mergedProperties.get("birthdate"), Matchers.equalTo(birthDate));
	}

	@Test public void getLpiMpiConflictingProperties_shouldConflictWhenPropertyValuesDoNotMatch() {
		Person lpiPerson = new Person();
		lpiPerson.setFirstName("FirstName");
		lpiPerson.setSex(Sex.F);
		Person mpiPerson = new Person();
		mpiPerson.setFirstName("Conflicting");
		mpiPerson.setSex(Sex.M);
		Map<String, Map<String, Object>> conflictedProperties = mergeService.getLpiMpiConflictingProperties(lpiPerson, mpiPerson);

		Assert.assertThat(conflictedProperties.size(), Matchers.is(2));
		Assert.assertThat(conflictedProperties.get("firstName").get("lpi").toString(), Matchers.equalTo("FirstName"));
		Assert.assertThat(conflictedProperties.get("firstName").get("mpi").toString(), Matchers.equalTo("Conflicting"));
		Assert.assertThat(conflictedProperties.get("sex").get("lpi").toString(), Matchers.equalTo("F"));
		Assert.assertThat(conflictedProperties.get("sex").get("mpi").toString(), Matchers.equalTo("M"));
	}

	@Test public void getLpiMpiConflictingProperties_shouldReturnEmptyCollectionIfBothLpiAndMpiAreNull() {
		Assert.assertThat(mergeService.getLpiMpiConflictingProperties(null, null).size(), Matchers.is(0));
	}

	@Test public void getLpiMpiMergedIdentifiers_shouldMergeIdentifiersIfTheyMatch() throws ParseException {
		Person lpiPerson = getMpiPerson("1-1-1-1", Type.cccLocalId);
		Person mpiPerson = getMpiPerson("1-1-1-1", Type.cccLocalId);
		
		Map<String, String> mergedIdentifiers = mergeService.getLpiMpiMergedIdentifiers(lpiPerson, mpiPerson);
		
		Assert.assertThat(mergedIdentifiers.size(), Matchers.is(1));
	}

	@Test public void getLpiMpiMergedIdentifiers_shouldPreferMpiIdIfIdentifierTypeIsNupi() throws ParseException {
		Person lpiPerson = getMpiPerson("1-1-1-1", Type.nupi);
		Person mpiPerson = getMpiPerson("1-1-1-2", Type.nupi);
		
		Map<String, String> mergedIdentifiers = mergeService.getLpiMpiMergedIdentifiers(lpiPerson, mpiPerson);
		
		Assert.assertThat(mergedIdentifiers.size(), Matchers.is(1));
		Assert.assertThat(mergedIdentifiers.get(Type.nupi.toString()), Matchers.equalTo("1-1-1-2"));
	}

	@Test public void getLpiMpiMergedIdentifiers_shouldReturnEmptyIfIdentifiersInConflict() throws ParseException {
		Person lpiPerson = getMpiPerson("1-1-1-1", Type.cccLocalId);
		Person mpiPerson = getMpiPerson("1-1-1-2", Type.cccLocalId);
		
		Map<String, String> mergedIdentifiers = mergeService.getLpiMpiMergedIdentifiers(lpiPerson, mpiPerson);
		
		Assert.assertThat(mergedIdentifiers.size(), Matchers.is(0));
	}
	
	@Test public void getLpiMpiMergedIdentifiers_shouldPreferNonEmptyIdentifiers() throws ParseException {
		Person person = getMpiPerson("1-1-1-1", Type.cccLocalId);
		
		Map<String, String> lpiPreferred = mergeService.getLpiMpiMergedIdentifiers(person, null);
		
		Assert.assertThat(lpiPreferred.size(), Matchers.is(1));
		Assert.assertThat(lpiPreferred.get(Type.cccLocalId.toString()), Matchers.equalTo("1-1-1-1"));
		
		Map<String, String> mpiPreferred = mergeService.getLpiMpiMergedIdentifiers(null, person);
		
		Assert.assertThat(mpiPreferred.size(), Matchers.is(1));
		Assert.assertThat(mpiPreferred.get(Type.cccLocalId.toString()), Matchers.equalTo("1-1-1-1"));
	}

	private List<PatientIdentifierType> getMockListOfIdentifiers() {
		List<PatientIdentifierType> identifierTypes = new ArrayList<PatientIdentifierType>();
		PatientIdentifierType cccLocalId = getCccLocalIdentifier();
		PatientIdentifierType cccUniqueId = getCccUniqueIdentifier();
		identifierTypes.add(cccLocalId);
		identifierTypes.add(cccUniqueId);
		return identifierTypes;
	}

	private PatientIdentifierType getCccLocalIdentifier() {
		PatientIdentifierType cccLocalId = new PatientIdentifierType();
		cccLocalId.setName("Patient Clinic Number");
		return cccLocalId;
	}

	private PatientIdentifierType getCccUniqueIdentifier() {
		PatientIdentifierType cccUniqueId = new PatientIdentifierType();
		cccUniqueId.setName("Unique Patient Number");
		return cccUniqueId;
	}

	private Person getMpiPerson(String identifier, Type identifierType) throws ParseException {
		PersonIdentifier personIdentifier = new PersonIdentifier();
		personIdentifier.setIdentifier(identifier);
		personIdentifier.setIdentifierType(identifierType);
		Person mpiPerson = new Person();
		mpiPerson.setFirstName("John");
		mpiPerson.setLastName("Junior");
		mpiPerson.setSex(Sex.F);
		mpiPerson.setPersonGuid(UUID.randomUUID().toString());
		mpiPerson.setFathersFirstName("John");
		mpiPerson.setFathersLastName("Senior");
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		Date date = dateFormat.parse("25/08/1999");
		mpiPerson.setBirthdate(date);
		mpiPerson.setPersonIdentifierList(Arrays.asList(personIdentifier));
		return mpiPerson;
	}

}
