package ru.andrikeev.android.synoptic.model;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import ru.andrikeev.android.synoptic.model.data.SuggestionModel;
import ru.andrikeev.android.synoptic.model.network.googleplaces.GooglePlacesApi;
import ru.andrikeev.android.synoptic.model.network.googleplaces.GooglePlacesService;
import ru.andrikeev.android.synoptic.model.network.googleplaces.ResponceConverter;
import ru.andrikeev.android.synoptic.model.network.googleplaces.places.Location;
import ru.andrikeev.android.synoptic.model.network.googleplaces.places.PlacesResponse;
import ru.andrikeev.android.synoptic.model.network.googleplaces.suggestions.SuggestionsResponse;
import ru.andrikeev.android.synoptic.model.network.openweather.OpenWeatherService;
import ru.andrikeev.android.synoptic.model.network.openweather.response.WeatherResponse;
import ru.andrikeev.android.synoptic.model.persistence.Weather;
import timber.log.Timber;

/**
 * Created by overtired on 28.07.17.
 */

public class CityResolver {
    private GooglePlacesService placesService;
    private OpenWeatherService weatherService;
    private ModelsConverter converter;

    @Inject
    public CityResolver(@NonNull GooglePlacesService placesService,
                        @NonNull OpenWeatherService weatherService,
                        @NonNull ModelsConverter converter){
        this.placesService = placesService;
        this.weatherService = weatherService;
        this.converter = converter;
    }

    public Single<List<SuggestionModel>> loadPredictions(@NonNull String input){
        return placesService.loadPredictions(input)
                .subscribeOn(Schedulers.io())
                .map(new Function<SuggestionsResponse, List<SuggestionModel>>() {
                    @Override
                    public List<SuggestionModel> apply(@NonNull SuggestionsResponse suggestionsResponse) throws Exception {
                        return ResponceConverter.toViewModel(suggestionsResponse);
                    }
                });
    }

    public Single<Long> loadCityId(String placeId){
        return placesService.loadPlace(placeId)
                .map(new Function<PlacesResponse, Location>() {
                    @Override
                    public Location apply(@NonNull PlacesResponse placesResponse) throws Exception {
                        Timber.d("Status",placesResponse.getStatus());
                        if(placesResponse.getStatus().equals(GooglePlacesApi.STATUS_OK)) {
                            return placesResponse.getResultPlace().getGeometry().getLocation();
                        }else {
                            throw new Exception("Couldn't load this city");
                        }
                    }
                })
                .flatMap(new Function<Location, SingleSource<WeatherResponse>>() {
                    @Override
                    public SingleSource<WeatherResponse> apply(@NonNull Location location) throws Exception {
                        return weatherService.getWeather(location.getLatitude(), location.getLongitude());
                    }
                })
                .map(new Function<WeatherResponse, Weather>() {
                    @Override
                    public Weather apply(@NonNull WeatherResponse weatherResponse) throws Exception {
                        return converter.toCacheModel(weatherResponse);
                    }
                }).map(new Function<Weather, Long>() {
                    @Override
                    public Long apply(@NonNull Weather weather) throws Exception {
                        return weather.getCityId();
                    }
                });
    }
}
