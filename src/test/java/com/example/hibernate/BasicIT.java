package com.example.hibernate;


import com.example.hibernate.entity.MovieActor;
import com.example.hibernate.external.model.MovieActorInformation;
import com.example.hibernate.external.model.MovieInformation;
import com.example.hibernate.external.model.MovieRatingInformation;
import com.example.hibernate.mapper.ActorMapper;
import com.example.hibernate.mapper.MovieMapper;
import com.example.hibernate.mapper.RatingMapper;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.instancio.Instancio;
import org.instancio.Model;
import org.instancio.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;


@SpringBootTest
@AutoConfigureMockMvc
public abstract class BasicIT {

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    protected MockMvc mvc;

    protected final static int ACTORS_NUMBER_BY_MOVIE = 10;
    protected final static int RATINGS_NUMBER_BY_MOVIE = 10;

    public static Model<MovieActorInformation> actorModel = Instancio.of(MovieActorInformation.class)
            .toModel();


    public static Model<MovieRatingInformation> ratingModel = Instancio.of(MovieRatingInformation.class)
            .generate(Select.field(MovieRatingInformation::note), generators -> generators.ints().range(8, 10))
            .generate(Select.field(MovieRatingInformation::comment), generators -> generators.oneOf("So good", "Awesome", "Good job"))
            .toModel();

    public static Model<MovieInformation> movieModel = Instancio.of(MovieInformation.class)
            .supply(Select.field(MovieInformation::ratings), () -> Instancio.ofList(ratingModel).size(RATINGS_NUMBER_BY_MOVIE).create())
            .supply(Select.field(MovieInformation::actors), () -> Instancio.ofList(actorModel).size(ACTORS_NUMBER_BY_MOVIE).create())
            .generate(Select.field(MovieInformation::countryCode), generators -> generators.oneOf("FR", "IT", "ES", "US", "UK", "CI", "BE", "CH"))
            .toModel();

    public static List<MovieInformation> getFakeMoviesInformation(int movieNumber) {
        return Instancio.ofList(movieModel).size(movieNumber).create();
    }


    protected void insertFakeMovies(int moviesNumber) {
        Session session = sessionFactory.openSession();
        session.setFetchBatchSize(30);
        Transaction tx = null;
        var moviesInformations = getFakeMoviesInformation(moviesNumber);
        try {
            tx = session.beginTransaction();

            moviesInformations.forEach(movieInformation -> {
                var movie = MovieMapper.fromMovieInformation(movieInformation);
                session.persist(movie);

                movieInformation.actors().forEach(movieActorInformation -> {
                    var actor = ActorMapper.fromMovieActorInformation(movieActorInformation);
                    session.persist(actor);
                    var movieActor = new MovieActor();
                    movieActor.setMovie(movie);
                    movieActor.setActor(actor);
                    movieActor.setCharacterName(movieActorInformation.characterName());
                    session.persist(movieActor);
                    movie.addMovieActor(movieActor);
                });

                movieInformation.ratings().forEach(movieRatingInformation -> {
                    var rating = RatingMapper.fromMovieRatingInformation(movieRatingInformation);
                    rating.setMovie(movie);
                    session.persist(rating);
                    movie.addRating(rating);
                });

            });


            tx.commit();
        } catch (RuntimeException e) {
            if (tx != null) tx.rollback();
            throw e; // or display error message
        } finally {
            System.out.println("\u001B[2J");
            session.close();
        }
    }

}

