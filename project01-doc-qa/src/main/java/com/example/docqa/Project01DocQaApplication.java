package com.example.docqa;

import com.example.docqa.ingest.Document;
import com.example.docqa.ingest.DocumentLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class Project01DocQaApplication {

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
			System.out.println("加载 " + docs.size() + " 篇");
			for (Document doc : docs) {
				System.out.println("  " + doc.source() + " (" + doc.content().length() + " 字)");
			}
		};
	}
}
