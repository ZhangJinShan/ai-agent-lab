package com.example.docqa;

import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class Project01DocQaApplication {

	private static final Logger log = LoggerFactory.getLogger(Project01DocQaApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(Project01DocQaApplication.class, args);
	}

	@Bean
	DocumentLoader documentLoader(@Value("${docqa.docs-dir}") String dir){
		return new DocumentLoader(dir);
	}

	@Bean
	CommandLineRunner verifyLoad(DocumentLoader loader) {
		return args -> {
			List<Document> docs = loader.loadAll();
			log.info("Loaded {} documents", docs.size());
			for (Document doc : docs) {
				log.info("  {} ({}字): {}", doc.source(), doc.content().length(), doc.content().substring(0, 100));
			}
		};
	}
}
