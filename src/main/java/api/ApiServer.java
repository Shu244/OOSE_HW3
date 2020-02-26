package api;

import com.google.gson.Gson;
import com.mashape.unirest.http.exceptions.UnirestException;
import dao.CourseDao;
import dao.DaoFactory;
import dao.DaoUtil;
import dao.ReviewDao;
import exception.ApiError;
import exception.DaoException;
import io.javalin.Javalin;
import io.javalin.plugin.json.JavalinJson;
import model.Course;
import model.Review;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ApiServer {

  public static boolean INITIALIZE_WITH_SAMPLE_DATA = true;
  public static int PORT = 7000;
  private static Javalin app;

  private ApiServer() {
    // This class is not meant to be instantiated!
  }

  public static void start() {
    CourseDao courseDao = DaoFactory.getCourseDao();
    ReviewDao reviewDao = DaoFactory.getReviewDao();

    if (INITIALIZE_WITH_SAMPLE_DATA) {
      DaoUtil.addSampleCourses(courseDao);
      DaoUtil.addSampleReviews(courseDao, reviewDao);
    }

    app = startJavalin();

    // Routing
    getHomepage();
    getCourses(courseDao);
    postCourses(courseDao);
    getReviewsForCourse(reviewDao);
    postReviewForCourse(reviewDao);

    // Handle exceptions
    app.exception(ApiError.class, (exception, ctx) -> {
      ApiError err = (ApiError) exception;
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put("status", err.getStatus());
      jsonMap.put("errorMessage", err.getMessage());
      ctx.status(err.getStatus());
      ctx.json(jsonMap);
    });

    // runs after every request (even if an exception occurred)
    app.after(ctx -> {
      // run after all requests
      ctx.contentType("application/json");
    });
  }

  public static void stop() {
    app.stop();
  }

  private static Javalin startJavalin() {
    Gson gson = new Gson();
    JavalinJson.setFromJsonMapper(gson::fromJson);
    JavalinJson.setToJsonMapper(gson::toJson);

    return Javalin.create().start(PORT);
  }

  private static void getHomepage() {
    app.get("/", ctx -> ctx.result("CourseReVU RESTful API"));
  }

  private static void getCourses(CourseDao courseDao) {
    // handle HTTP Get request to retrieve all courses
    app.get("/courses", ctx -> {
      List<Course> courses = courseDao.findAll();
      ctx.json(courses);
      ctx.status(200); // everything ok!
    });
  }

  private static void postCourses(CourseDao courseDao) {
    // client adds a course through HTTP POST request
    app.post("/courses", ctx -> {
      Course course = ctx.bodyAsClass(Course.class);
      try {
        courseDao.add(course);
        ctx.status(201); // created successfully
        ctx.json(course);
      } catch (DaoException ex) {
        throw new ApiError(ex.getMessage(), 500); // server internal error
      }
    });
  }

  private static void getReviewsForCourse(ReviewDao reviewDao) {
    // handle HTTP Get request to retrieve all reviews for a course
    app.get("/courses/:id/reviews", ctx -> {
      try {
        // Parse as int the courseId from ctx's path parameters
        int courseId = Integer.parseInt(ctx.pathParam("id"));
        // Retrieve course reviews and format them as json
        List<Review> reviews = reviewDao.findByCourseId(courseId);
        ctx.json(reviews);
      } catch (NumberFormatException e) {
        // If the number cannot be parsed, return an empty list.
        // We chose not to throw an error since users
        // can see nothing was retrieved.
        List<Review> empty = new ArrayList<>();
        ctx.json(empty);
      }
      ctx.status(200);
    });
  }

  private static void postReviewForCourse(ReviewDao reviewDao) {
    // client adds a review for a course (given its id) using HTTP POST request
    app.post("/courses/:id/reviews", ctx -> {
      // Get Review from ctx
      Review review = ctx.bodyAsClass(Review.class);
      try {
        // Try to parse courseId
        int courseId = Integer.parseInt(ctx.pathParam("id"));

        // If courseId in path does not match the review's courseId, throw an error
        if(courseId != review.getCourseId()) {
          throw new NumberFormatException("Mismatched ID");
        }
        // Add the review to the database via DAO and format it as json
        reviewDao.add(review);
        ctx.status(201); // created successfully
        ctx.json(review);
      } catch (NumberFormatException ex) {
        // This error is thrown when the courseId in the path cannot be parsed OR
        // if the courseId in the path does not match the actual courseId in the Review
        throw new ApiError(ex.getMessage(), 400 /*Request error*/);
      } catch (DaoException ex) {
        // If we tried to add a review to a non-existing course, this is thrown.
        throw new ApiError(ex.getMessage(), 500); // server internal error
      }
    });
  }
}
