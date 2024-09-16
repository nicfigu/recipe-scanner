package com.recipe.recipeai.controller;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.http.rest.RequestOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.recipeai.dto.RecipeDetailsDto;
import com.recipe.recipeai.dto.RecipeDishDetailsDto;
import com.recipe.recipeai.dto.RecipeImageDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/recipes")
@CrossOrigin(origins = "*")
public class RecipesController extends BaseController {
    @Value("${spring.ai.azure.bing.endpoint}")
    private String bingEndpoint;
    @Value("${spring.ai.azure.bing.api-key}")
    private String bingApiKey;
    protected RecipesController(OpenAIClient openAIClient) {
        super(openAIClient);
    }
    @PostMapping("/generate")
    //@CrossOrigin("http://localhost:3000")
    public ResponseEntity<String> generateRecipe(@RequestBody @Valid RecipeDishDetailsDto details){
        // The base prompt for sending to model
        String prompt = String.format("""
                I want to make %s. Give me the recipe for this dish.
                Include calories, protein, fats and carbs per serving.""", details.dishName());

        ChatCompletionsOptions options = getChatCompletionsOptions(prompt);
        // Call Azure OpenAI to get response
        ChatCompletions completions = openAIClient.getChatCompletions(modelName, options);
        // Fetch the text content
        return ResponseEntity.ok(completions.getChoices().get(0).getMessage().getContent().strip());
    }


    @PostMapping("/generateFull")
    public ResponseEntity<String> generateRecipes(@RequestBody @Valid RecipeDetailsDto details){
        // building prompt for sending to model
        StringBuilder stringBuilder = new StringBuilder().append(String.format("Ingredients: %s. ", details.ingredientsToString()));
        if (details.dietRestrictions() != null && !details.dietRestrictions().isEmpty())
            stringBuilder.append(String.format("Dietary restrictions: %s. ", details.dietRestrictionsToString()));
        if (details.preferences() != null && !details.preferences().isEmpty())
            stringBuilder.append(String.format("Preferences: %s. ", details.preferencesToString()));
        if (details.spices() != null && !details.spices().isEmpty())
            stringBuilder.append(String.format("Spices: %s. ", details.spicesToString()));
        String prompt = stringBuilder.append(
                """
                Provide a valid JSON response of format: [{"name": "The dish name", "content": "the recipe text
                (include the calories, protein, fats, carbs per serving)"}]. Give 2-4 dish names and their recipes
                (using these ingredients, spices and aligning with restrictions/preferences).""").toString();

        ChatCompletionsOptions options = getChatCompletionsOptions(prompt);

        // Call Azure OpenAI to get response
        ChatCompletions completions = openAIClient.getChatCompletions(modelName, options);
        // Fetch the text content
        var response = completions.getChoices().get(0).getMessage().getContent().strip();
        response = " " + response + " ";
        if(!response.endsWith("}]")) response = response + "\"}]";
        System.out.println(response);
        response = response.substring(response.indexOf('['), response.indexOf(']') + 1);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }
    @PostMapping("/images")
    public ResponseEntity<?> findImages(@RequestBody List<String> dishes){
        if(dishes == null || dishes.isEmpty()) return ResponseEntity.badRequest().body("At least 1 dish to be provided");
        var response = dishes.stream().map(name -> new RecipeImageDto(name, searchImage(name))).toList();
        return ResponseEntity.ok().body(response);
    }
    private String searchImage(String query) {
        String url = String.format(bingEndpoint, query.replace(" ", "%20"));
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            System.out.println(url);
            HttpGet request = new HttpGet(url);
            request.addHeader("Ocp-Apim-Subscription-Key", bingApiKey);
            HttpResponse response = httpClient.execute(request);
            String jsonResponse = EntityUtils.toString(response.getEntity());

            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray imageResults = jsonObject.getJSONArray("value");
            // Returning the first image result
            System.out.println(query);
            System.out.println(imageResults.getJSONObject(0));
            return imageResults.getJSONObject(0).getString("contentUrl");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
